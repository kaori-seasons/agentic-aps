#!/usr/bin/env python3
"""Disposable real HTTP benchmark with identical inputs and a shared wall budget."""
import argparse, json, subprocess, platform, os, time
import e2e_adaptive as e
import e2e_cpsat as cp
import e2e_cpsat_optimization as old
from e2e_demand_planning import Client, ok
OUT=e.ROOT/'docs/e2e/cpsat-search'

def process_cpu(pid):
    if pid is None:return None
    # ps TIME is cumulative process CPU, includes JVM worker/GC threads, excludes child processes.
    raw=subprocess.check_output(['ps','-p',str(pid),'-o','time='],text=True).strip()
    days=0
    if '-' in raw:day,raw=raw.split('-',1);days=int(day)
    values=[float(x) for x in raw.split(':')]
    seconds=0
    for value in values:seconds=seconds*60+value
    return round((days*86400+seconds)*1000)

def acceptance(c):
    report=dict(synthetic=True,status='RUNNING',cases=[])
    def passed(name,**extra):
        report['cases'].append(dict(name=name,status='PASS',**extra));e.save('acceptance',report)
    old.cancel(c);e.fixture(4)
    old.configure(c,seconds=30,timefoldWarmStart=True,sizeAwareBudget=True)
    a,r,elapsed=old.run(c,'acceptance-initial-seed',4)
    x=r['cpSat'];assert x['hybrid']['initialSeedOnly'] and x['verified'] and x['adoption']['accepted']
    assert x['movableTasks']>0 and x['worker']['cost']<=x['baselineCost']
    cp.assert_persisted(r['tasks'],'jvs_plan_task_pending')
    passed('initial verified seed produces publishable candidate',auditId=a['id'])
    ok(c.call('/smart-scheduling/plan/pending/confirm',{}))
    cp.assert_persisted(r['tasks'],'jvs_plan_task')
    old.configure(c,seconds=15,timefoldWarmStart=True,minNetImprovement=1000000000000)
    a,r,elapsed=old.run(c,'acceptance-existing-baseline',4)
    x=r['cpSat'];assert x['hybrid']['existingBaseline'] and not x['hybrid']['initialSeedOnly']
    assert 'timefoldMillis' not in x['hybrid'] and a['status']=='BASELINE_RETAINED' and cp.pending()==0
    assert x['request']['optimization']['changedWeight']==1000
    passed('confirmed baseline skips seed and preserves adoption threshold',auditId=a['id'])
    old.configure(c,seconds=1,timefoldWarmStart=True,reuseWorker=False,minNetImprovement=1000000000000)
    a,r,elapsed=old.run(c,'acceptance-short-budget',4)
    x=r['cpSat'];assert a['status']=='BASELINE_RETAINED' and x['verified'] and cp.pending()==0
    passed('short cold-worker budget preserves verified baseline',auditId=a['id'],solverStatus=x['worker']['status'],message=x['worker']['message'])
    old.cancel(c);e.fixture(12)
    old.configure(c,seconds=30,timefoldWarmStart=True,sizeAwareBudget=False,timefoldBudgetPercent=80)
    ok(c.call('/smart-scheduling/generate',dict(planningStrategyId='ps-demo-forward')))
    time.sleep(2)
    old.cancel(c)
    for _ in range(100):
        status=e.sql("SELECT status FROM jvs_solve_audit WHERE tenant_id='demo' ORDER BY created_at DESC LIMIT 1")
        if status=='CANCELLED':break
        time.sleep(.1)
    assert status=='CANCELLED' and cp.pending()==0
    passed('cancelling during Timefold seed never publishes fallback')
    report['status']='PASS';e.save('acceptance',report)
    print('ACCEPTANCE PASS',flush=True)

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--disposable',required=True,action='store_true')
    p.add_argument('--orders',nargs='+',type=int,default=[4,8,12])
    p.add_argument('--seeds',nargs='+',type=int,default=[17,29,43])
    p.add_argument('--modes',nargs='+',default=['TIMEFOLD','CP_SAT','HYBRID'])
    p.add_argument('--seconds',type=int,default=30)
    p.add_argument('--acceptance-only',action='store_true')
    p.add_argument('--resume',action='store_true')
    p.add_argument('--backend-pid',type=int,help='Optional isolated backend PID for process CPU accounting')
    args=p.parse_args()
    assert e.sql('SELECT DATABASE()')=='aps_e2e'
    assert 10<=args.seconds<=120 and all(1<=n<=30 for n in args.orders)
    assert set(args.modes)<=set(['TIMEFOLD','CP_SAT','HYBRID','PORTFOLIO','TWO_WORKERS'])
    e.OUT=cp.OUT=OUT
    c=Client(e.BASE);c.login()
    if args.acceptance_only:acceptance(c);return
    report=json.loads((OUT/'matrix.json').read_text()) if args.resume else dict(synthetic=True,budgetSeconds=args.seconds,runs=[])
    assert report['budgetSeconds']==args.seconds
    report['environment']=dict(platform=platform.platform(),cpuCount=os.cpu_count(),backendPid=args.backend_pid)
    report['status']='RUNNING';e.save('matrix',report)
    for count in args.orders:
        for seed in args.seeds:
            for mode in args.modes:
                if any(x['orders']==count and x['seed']==seed and x['mode']==mode for x in report['runs']):continue
                old.cancel(c);e.fixture(count)
                old.configure(c,'TIMEFOLD' if mode=='TIMEFOLD' else 'ORTOOLS_CP_SAT',seed,args.seconds,
                    adaptive_mode='SCALE_AWARE',timefoldWarmStart=mode=='HYBRID',sizeAwareBudget=True,
                    searchMode='PORTFOLIO' if mode=='PORTFOLIO' else 'AUTOMATIC',numWorkers=2 if mode=='TWO_WORKERS' else 1)
                cpu_before=process_cpu(args.backend_pid)
                a,r,elapsed=old.run(c,f'{count}-{seed}-{mode}',count,None)
                cpu_after=process_cpu(args.backend_pid)
                m=old.metrics(a,r,elapsed);x=r.get('cpSat',{})
                m['backendCpuMillis']=None if cpu_before is None else cpu_after-cpu_before
                m.update(orders=count,tasks=count*6,seed=seed,mode=mode,hybrid=x.get('hybrid'),workerCpuMillis=x.get('workerCpuMillis'),
                    solverStatus=x.get('worker',{}).get('status'),movableTasks=x.get('movableTasks'),
                    refinementRounds=x.get('refinementRounds'),workerCpuComplete=x.get('workerCpuComplete'))
                peers=[v for v in report['runs'] if v['orders']==count and v['seed']==seed]
                assert all(v['inputFingerprint']==m['inputFingerprint'] for v in peers),'Different semantic input'
                if m['feasible'] and mode=='HYBRID' and x.get('baselineAccepted'):
                    assert x['worker']['cost']<=x['baselineCost']
                    assert x['adoption']['accepted'] and x['hybrid']['initialSeedOnly']
                    rounds=x.get('refinementRounds',[])
                    assert x['worker']['cost']<=min(v['cost'] for v in rounds)
                    scopes=[v['movableTasks'] for v in rounds]
                    assert scopes==sorted(set(scopes)) and max(scopes)<=60
                report['runs'].append(m);e.save('matrix',report)
                print(count,seed,mode,m['feasible'],m['cost'],round(elapsed,3),m['hybrid'],flush=True)
    report['status']='COMPLETE';e.save('matrix',report)
if __name__=='__main__':main()

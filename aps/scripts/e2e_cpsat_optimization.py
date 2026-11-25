#!/usr/bin/env python3
"""Disposable-only real HTTP benchmark and worker/local/adoption acceptance. No solver mocks."""
import argparse, json, time, hashlib, statistics
from datetime import datetime
from pathlib import Path
import e2e_adaptive as e
import e2e_cpsat as cp
from e2e_demand_planning import Client, ok
OUT=e.ROOT/'docs/e2e/cpsat-optimization'


def configure(c, engine='ORTOOLS_CP_SAT', seed=17, seconds=30, adaptive_mode='FIXED', **options):
    row=cp.strategy(c,engine=engine,seconds=seconds)
    row['name']='离散装配｜稳定性与局部优化验收'
    row['config']['randomSeed']=seed
    row['config']['cpSat'].update(reuseWorker=True,optimizationScope='FULL',focusTaskCodes=[],neighborhoodDepth=1,
        maxNeighborhoodTasks=60,changedTaskWeight=1000,resourceChangeWeight=3000,startShiftMinuteWeight=1,
        minNetImprovement=1,minTardinessImprovementMinutes=0,maxChangedTaskPercent=100)
    row['config']['cpSat'].update(options)
    if engine=='TIMEFOLD':
        row['config']['adaptive']=dict(enabled=True,populationSize=4,generations=1,totalSeconds=seconds,
            trialSeconds=max(1,seconds//3),trialScoreLimit=1000000,warmStartEnabled=True,budgetMode=adaptive_mode)
    ok(c.call('/planning-strategy',row,method='PUT'))


def cancel(c):ok(c.call('/smart-scheduling/plan/pending/cancel',{}))
def save(name,value):e.save(name,value)
def run(c,label,count,expect=True):
    a,r,elapsed=cp.generate(c,label,success=expect)
    if r.get('score','').startswith('0hard/'):
        e.validate(a,r,count)
    return a,r,elapsed


def metrics(a,r,elapsed):
    p=json.loads(a['inputJson'])['problem']; by={t['taskCode']:t for t in r.get('tasks',[])}
    late=minutes=0
    for t in p['tasks']:
        if t.get('endTask') and t.get('order',{}).get('deliveryTime') and t['code'] in by:
            delta=(datetime.fromisoformat(by[t['code']]['end'])-datetime.fromisoformat(t['order']['deliveryTime'])).total_seconds()
            if delta>0:late+=1;minutes+=int(delta//60)
    # Ignore generated database IDs and audit timestamps; retain all scheduling semantics.
    semantic=dict(planTime=p['planTime'],tasks=[{k:t.get(k) for k in ['code','priority','quantity','process','frontTaskCodes','notBefore','endTask']} |
        dict(frontTaskCodes=sorted(t.get('frontTaskCodes') or []),due=t.get('order',{}).get('deliveryTime'),material=t.get('material',{}).get('id')) for t in sorted(p['tasks'],key=lambda t:t['code'])],
        resources=[dict(id=x['id'],workCalendars=x['workCalendars'],downtimes=x.get('downtimes')) for x in sorted(p['resources'],key=lambda x:x['id'])],
        weights=p['strategy']['config']['constraintWeightConfig'])
    fingerprint=hashlib.sha256(json.dumps(semantic,sort_keys=True).encode()).hexdigest()
    cps=r.get('cpSat',{});worker=cps.get('worker',{})
    return dict(auditId=a['id'],status=a['status'],feasible=r.get('score','').startswith('0hard/'),score=r.get('score'),
        cost=-int(r['score'].split('/')[1].removesuffix('soft')) if r.get('score','').startswith('0hard/') else None,
        inputFingerprint=fingerprint,httpSeconds=elapsed,auditSeconds=(datetime.fromisoformat(a['finishedAt'])-datetime.fromisoformat(a['createdAt'])).total_seconds(),
        lateTerminalTasks=late if r.get('score','').startswith('0hard/') else None,tardinessMinutes=minutes if r.get('score','').startswith('0hard/') else None,latestEnd=max((t['end'] for t in by.values()),default=None),
        workerReused=cps.get('workerReused'),performance=worker.get('performance'),solveSeconds=worker.get('solveSeconds'),
        adaptiveTrials=len(r.get('adaptive',{}).get('trials',[])))


def matrix(c,args):
    report=dict(status='RUNNING',synthetic=True,orders=args.orders,seeds=args.seeds,budgetSeconds=args.seconds,
        singleThread=True,comparison='Independent full planning, no historical baseline; identical six business weights',runs=[])
    if args.resume:
        report=json.loads((OUT/'matrix.json').read_text())
        assert report['orders']==args.orders and report['seeds']==args.seeds and report['budgetSeconds']==args.seconds
        for old in report['runs']:
            evidence=json.loads((OUT/f"matrix-{old['orders']}-{old['seed']}-{old['engine']}.json").read_text())
            old.update(metrics(evidence['audit'],json.loads(evidence['audit']['resultJson']),old['httpSeconds']))
    save('matrix',report)
    for count in args.orders:
        for seed in args.seeds:
            pair=[]
            for engine in ['TIMEFOLD','ORTOOLS_CP_SAT']:
                existing=next((x for x in report['runs'] if x['orders']==count and x['seed']==seed and x['engine']==engine),None)
                if existing is not None:pair.append(existing);continue
                cancel(c);e.fixture(count);configure(c,engine,seed,args.seconds)
                a,r,elapsed=run(c,f'matrix-{count}-{seed}-{engine}',count,None)
                m=metrics(a,r,elapsed);m.update(engine=engine,orders=count,tasks=count*6,seed=seed)
                if engine=='ORTOOLS_CP_SAT' and m['feasible']:assert not r['cpSat']['baselineAccepted']
                report['runs'].append(m);pair.append(m);save('matrix',report)
                print('MATRIX',count,seed,engine,m['feasible'],m['cost'],elapsed,flush=True)
            assert pair[0]['inputFingerprint']==pair[1]['inputFingerprint'],'Mismatched benchmark inputs'
    report['status']='COMPLETE';save('matrix',report)


def scale_aware(c,args):
    report=dict(status='RUNNING',synthetic=True,budgetSeconds=args.seconds,runs=[])
    original=json.loads((OUT/'matrix.json').read_text())
    for count in args.orders:
        for seed in args.seeds:
            cancel(c);e.fixture(count);configure(c,'TIMEFOLD',seed,args.seconds,adaptive_mode='SCALE_AWARE')
            a,r,elapsed=run(c,f'scale-aware-{count}-{seed}',count,None)
            m=metrics(a,r,elapsed);m.update(engine='TIMEFOLD_SCALE_AWARE',orders=count,tasks=count*6,seed=seed)
            ref=next(x for x in original['runs'] if x['orders']==count and x['seed']==seed and x['engine']=='ORTOOLS_CP_SAT')
            assert ref['inputFingerprint']==m['inputFingerprint']
            report['runs'].append(m);save('scale-aware',report)
            print('SCALE_AWARE',count,seed,m['feasible'],m['cost'],elapsed,flush=True)
    report['status']='COMPLETE';save('scale-aware',report)


def acceptance(c,args):
    report=dict(status='RUNNING',cases=[])
    def passed(name,**details):
        report['cases'].append(dict(name=name,status='PASS',**details));save('acceptance',report);print('PASS',name,flush=True)
    cancel(c);e.fixture(4)
    # Start a new bounded process, then repeat the exact input through the pooled route.
    configure(c,seconds=30)
    a,r,_=run(c,'pool-first',4);first=r['cpSat'];assert first['verified']
    cancel(c)
    a,r,_=run(c,'pool-repeat',4);second=r['cpSat']
    assert second['workerReused'] and first['workerPid']==second['workerPid']
    assert second['worker']['performance']['modelCacheHit']
    passed('worker and exact model reused',first=first['worker']['performance'],repeat=second['worker']['performance'])
    # Changed business constraints must invalidate a compiled model.
    cancel(c);e.sql("UPDATE jvs_production_order SET delivery_time=DATE_ADD(delivery_time,INTERVAL 1 HOUR) WHERE tenant_id='demo'")
    a,r,_=run(c,'cache-invalidation',4);assert not r['cpSat']['worker']['performance']['modelCacheHit']
    passed('delivery-time change invalidates model cache')
    ok(c.call('/smart-scheduling/plan/pending/confirm',{}));cp.assert_persisted(r['tasks'],'jvs_plan_task')
    baseline=r['tasks'];focus=max(baseline,key=lambda x:x['end'])['taskCode']
    configure(c,seconds=15,optimizationScope='LOCAL',focusTaskCodes=[focus],neighborhoodDepth=1,
        minNetImprovement=1000000000000)
    a,r,_=run(c,'local-retained',4);x=r['cpSat'];assert a['status']=='BASELINE_RETAINED' and cp.pending()==0
    assert not x['adoption']['accepted'];assert 0<x['movableTasks']<24
    assert r['tasks']==baseline or {t['taskCode']:(t['resourceId'],t['start'],t['end']) for t in r['tasks']}=={t['taskCode']:(t['resourceId'],t['start'],t['end']) for t in baseline}
    movable=set(x['request']['optimization']['movableTasks']);base={t['taskCode']:t for t in baseline}
    for t in x['worker']['assignments']:
        if t['task'] not in movable:
            b=base[t['task']];origin=x['request']['originEpochMillis']
            assert t['resource']==b['resourceId']
            assert t['start']==round((datetime.fromisoformat(b['start'])-datetime(1970,1,1)).total_seconds()*1000)-origin
    passed('local exterior fixed and threshold retains original plan',auditId=a['id'],movable=x['movableTasks'],decision=x['adoption'])
    # Candidate API must not allow a retained solve to be confirmed.
    rejected=c.call('/smart-scheduling/plan/pending/confirm',{});assert rejected['code']!=0
    passed('retained result cannot be confirmed',responseCode=rejected['code'])
    # A local solve without a valid baseline is never silently expanded to a full solve.
    cancel(c);e.fixture(4);configure(c,seconds=15,optimizationScope='LOCAL')
    a,r,_=run(c,'local-missing-baseline',4,False);assert cp.pending()==0
    passed('missing local baseline rejected')
    # Cancellation kills the leased native process; the next solve obtains a fresh healthy worker.
    configure(c,seconds=45);ok(c.call('/smart-scheduling/generate',dict(planningStrategyId='ps-demo-forward')));time.sleep(2)
    cancel(c)
    for _ in range(100):
        status=e.sql("SELECT status FROM jvs_solve_audit WHERE tenant_id='demo' ORDER BY created_at DESC LIMIT 1")
        if status=='CANCELLED':break
        time.sleep(.1)
    assert status=='CANCELLED' and cp.pending()==0
    configure(c,seconds=30);a,r,_=run(c,'after-cancel',4);assert not r['cpSat']['workerReused']
    passed('cancel destroys worker and next request recovers')
    # Leave a candidate for UI preview and confirmation. Subsequent local-retained UI evidence is captured separately.
    report.update(status='PASS',candidateAudit=a['id']);save('acceptance',report)


def demo(c):
    cancel(c);e.fixture(4);configure(c,seconds=30)
    a,r,_=run(c,'demo-initial',4);ok(c.call('/smart-scheduling/plan/pending/confirm',{}))
    last=max(r['tasks'],key=lambda t:t['end']);code=last['taskCode'].replace("'","''")
    # Deliberately delay one final operation by exactly a week: same weekday/calendar,
    # no later operation can overlap it. This is a documented synthetic fixture, not an algorithm result.
    statement="UPDATE jvs_plan_task SET start_time=DATE_ADD(start_time,INTERVAL 7 DAY),end_time=DATE_ADD(end_time,INTERVAL 7 DAY) WHERE tenant_id='demo' AND code='"+code+"';"
    (OUT/'synthetic-delayed-terminal.sql').write_text(statement+'\n');e.sql(statement)
    configure(c,seconds=15,optimizationScope='LOCAL',focusTaskCodes=[last['taskCode']],neighborhoodDepth=0,
        minNetImprovement=1000000000000,maxChangedTaskPercent=10)
    a,r,_=run(c,'demo-threshold-rejected',4);x=r['cpSat']
    assert x['adoption']['changedTasks']==1 and x['adoption']['netGain']>0
    assert x['adoption']['reason']=='INSUFFICIENT_NET_BENEFIT' and a['status']=='BASELINE_RETAINED' and cp.pending()==0
    configure(c,seconds=15,optimizationScope='LOCAL',focusTaskCodes=[last['taskCode']],neighborhoodDepth=0,
        minNetImprovement=100,maxChangedTaskPercent=10)
    a,r,_=run(c,'demo-local-accepted',4);x=r['cpSat']
    assert x['adoption']['accepted'] and x['adoption']['changedTasks']==1 and x['worker']['performance']['modelCacheHit']
    assert a['status']=='FEASIBLE' and cp.pending()==24
    cp.assert_persisted(r['tasks'],'jvs_plan_task_pending')
    save('demo',dict(status='PASS',candidateAudit=a['id'],focusTask=last['taskCode'],adoption=x['adoption'],
        syntheticPerturbation='One last task delayed by exactly seven calendar days before optimization'))
    print('PASS local accepted, exactly one task changes, pending awaiting browser confirmation',flush=True)


def main():
    p=argparse.ArgumentParser();p.add_argument('--disposable',required=True,action='store_true')
    p.add_argument('--scale-aware-only',action='store_true')
    p.add_argument('--resume',action='store_true')
    p.add_argument('--demo-only',action='store_true')
    p.add_argument('--matrix-only',action='store_true');p.add_argument('--acceptance-only',action='store_true')
    p.add_argument('--orders',nargs='+',type=int,default=[4,8,12]);p.add_argument('--seeds',nargs='+',type=int,default=[17,29,43])
    p.add_argument('--seconds',type=int,default=30);args=p.parse_args()
    assert all(1<=n<=30 for n in args.orders) and 10<=args.seconds<=120
    assert e.sql('SELECT DATABASE()')=='aps_e2e'
    e.OUT=OUT;cp.OUT=OUT
    c=Client(e.BASE);c.login()
    if args.demo_only:demo(c);return
    if args.scale_aware_only:scale_aware(c,args);return
    if not args.acceptance_only:matrix(c,args)
    if not args.matrix_only:acceptance(c,args)

if __name__=='__main__':
    try:main()
    except Exception as error:
        OUT.mkdir(parents=True,exist_ok=True)
        (OUT/'last-failure.json').write_text(json.dumps(dict(status='FAILED',message=str(error),
            occurredAt=datetime.now().isoformat()),ensure_ascii=False,indent=2))
        raise

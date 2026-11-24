#!/usr/bin/env python3
"""Real HTTP comparison on disposable synthetic data. Does not overwrite V1 evidence.

FIXED cold vs FIXED warm isolates warm-start effects under identical configured limits.
SCALE_AWARE warm tests budget allocation separately; compare wall caps, not score caps.
"""
import argparse
import hashlib
import json
import time
from pathlib import Path
import e2e_adaptive as e
from e2e_demand_planning import Client, ok


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--scales', default='6,12,24')
    parser.add_argument('--seeds', default='17')
    parser.add_argument('--resume', action='store_true', help='Resume same-day saved observations, including a terminal budget rejection')
    parser.add_argument('--output', type=Path, default=e.ROOT/'docs/e2e/adaptive-warm/comparison')
    args = parser.parse_args()
    e.OUT = args.output.resolve()
    c = Client(e.BASE); c.login()
    report = dict(status='RUNNING', synthetic=True, planStart=e.DAY.isoformat(), cases=[])
    if args.resume:
        prior=json.loads((e.OUT/'comparison.json').read_text())
        assert prior['planStart']==e.DAY.isoformat()
        report['cases']=prior['cases']
    e.save('comparison',report)
    try:
        for count in map(int,args.scales.split(',')):
            # Common wall/score limits for both FIXED arms. Larger cases need construction headroom.
            total, trial, scores = (45,5,2000) if count<=6 else ((165,20,8000) if count<=12 else (500,120,22000))
            for seed in map(int,args.seeds.split(',')):
                for mode,warm in [('FIXED',False),('FIXED',True),('SCALE_AWARE',True)]:
                    label = f'{count*6}-tasks-{seed}-{mode.lower()}-' + ('warm' if warm else 'cold')
                    if any(r['name']==label for r in report['cases']): continue
                    saved=e.OUT/(label+'.json')
                    reused=None
                    if args.resume and saved.exists():
                        raw=json.loads(saved.read_text())
                        if raw['progress']['status'] in ('SUCCESS','FAILURE'):
                            reused=raw
                            if raw['progress']['status']=='FAILURE':
                                assert e.sql("SELECT COUNT(*) FROM jvs_plan_task_pending WHERE tenant_id='demo'")=='0'
                    ok(c.call('/smart-scheduling/plan/pending/cancel',{}))
                    e.fixture(count)
                    e.strategy(c,True,count)
                    row = ok(c.call('/planning-strategy/page?current=1&size=10'))['records'][0]
                    row['config']['randomSeed']=seed
                    row['config']['adaptive'].update(warmStartEnabled=warm,budgetMode=mode,totalSeconds=total,
                                                      trialSeconds=trial,trialScoreLimit=scores)
                    ok(c.call('/planning-strategy',row,method='PUT'))
                    actual = ok(c.call('/planning-strategy/page?current=1&size=10'))['records'][0]['config']['adaptive']
                    assert actual['warmStartEnabled']==warm and actual['budgetMode']==mode, actual
                    print('RUN',label,flush=True)
                    if reused:
                        audit=reused['audit']; result=json.loads(audit['resultJson'])
                        # Interrupted harness may have saved a terminal response before its assertion.
                        elapsed=None
                    else:
                        began=time.monotonic()
                        ok(c.call('/smart-scheduling/generate',{'planningStrategyId':'ps-demo-forward'}))
                        audit,result,_=e.await_solve(c,label,success=None)
                        elapsed=time.monotonic()-began
                    feasible='score' in result and result['score'].startswith('0hard/')
                    if feasible:
                        checks=e.validate(audit,result,count)
                    else:
                        assert '自适应求解预算内未找到硬约束可行解' in result.get('message',''), result
                        assert result['adaptive']['selectedTrial']==-1
                        assert not any(t['status']=='FEASIBLE' for t in result['adaptive']['trials'])
                        assert e.sql("SELECT COUNT(*) FROM jvs_plan_task_pending WHERE tenant_id='demo'")=='0'
                        checks=dict(tasks=count*6,trials=len(result['adaptive']['trials']),selected=-1,
                                    stop=result['adaptive']['stopReason'])
                    adaptive=result['adaptive']; trials=adaptive['trials']
                    assert adaptive['algorithm']=='JDE_TIMEFOLD_V2'
                    assert adaptive['budget']['activeTasks']==count*6
                    assert adaptive['warmStartEnabled']==warm
                    problem=json.loads(audit['inputJson'])['problem']
                    request=problem['strategy']['config']['adaptive']
                    assert request['warmStartEnabled']==warm and request['budgetMode']==mode
                    assert request['totalSeconds']==total and request['trialSeconds']==trial and request['trialScoreLimit']==scores
                    # Parameters differ; business input must remain byte-equivalent after canonicalization.
                    problem['strategy'].pop('config',None)
                    for order in problem['orders']:
                        for key in ('id','createTime','updateTime'): order.pop(key,None)
                    problem['orders'].sort(key=lambda order: order['code'])
                    problem['tasks'].sort(key=lambda task: task['code'])
                    for task in problem['tasks']:
                        task.pop('id',None)
                        for key in ('id','createTime','updateTime'): task['order'].pop(key,None)
                        for key in ('frontTaskCodes','nextTaskCodes'):
                            if task.get(key): task[key].sort()
                    fingerprint=hashlib.sha256(json.dumps(problem,sort_keys=True).encode()).hexdigest()
                    peers=[r for r in report['cases'] if r['tasks']==count*6 and r['seed']==seed]
                    assert not peers or peers[0]['inputFingerprint']==fingerprint, 'Comparison business inputs differ'
                    cold=sum(t['execution']['constructionEnabled'] for t in trials)
                    warm_trials=[t for t in trials if t['execution']['startMode']=='WARM']
                    assert cold+len(warm_trials)==len(trials)
                    if warm and feasible:
                        first_feasible=next(t['index'] for t in trials if t['status']=='FEASIBLE')
                        assert all(t['execution']['startMode']=='WARM' for t in trials[first_feasible+1:]), 'Feasible incumbent was not reused'
                        for t in warm_trials:
                            ex=t['execution']; assert not ex['constructionEnabled'] and not ex['fallbackReason']
                            assert ex['seedTrial']<t['index']
                            seed_trial=trials[ex['seedTrial']]
                            assert ex['seedScore']==seed_trial['score'] and seed_trial['status']=='FEASIBLE'
                            if t['status']=='FEASIBLE':
                                assert int(t['score'].split('/')[1][:-4])>=int(ex['seedScore'].split('/')[1][:-4])
                    elif not warm or not feasible:
                        assert cold==len(trials)
                    record=dict(name=label,status='PASS',outcome='FEASIBLE' if feasible else 'NO_FEASIBLE_WITHIN_BUDGET',
                                seed=seed,mode=mode,warm=warm,seconds=round(elapsed,3) if elapsed is not None else None,
                                auditSeconds=round((e.datetime.fromisoformat(audit['finishedAt'])-
                                                    e.datetime.fromisoformat(audit['createdAt'])).total_seconds(),3),
                                score=result.get('score'),coldTrials=cold,warmTrials=len(warm_trials),
                                preparationMillis=sum(t['execution']['preparationMillis'] for t in trials),
                                solveMillis=sum(t['execution']['solveMillis'] for t in trials),
                                budget=adaptive['budget'],inputFingerprint=fingerprint,auditId=audit['id'],**checks)
                    report['cases'].append(record); e.save('comparison',report)
                    print('PASS',json.dumps(record,ensure_ascii=False),flush=True)
        report['status']='PASS'
    except Exception as error:
        report.update(status='FAIL',error=str(error)); raise
    finally:
        e.save('comparison',report)
    ok(c.call('/smart-scheduling/plan/pending/cancel',{}))


if __name__=='__main__':
    main()

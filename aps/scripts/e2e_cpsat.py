#!/usr/bin/env python3
"""Real HTTP -> APS -> isolated OR-Tools JNI -> preview/confirm. Disposable local DB only."""
import argparse
import json
import time
from datetime import datetime, timedelta
from pathlib import Path
import e2e_adaptive as e
from e2e_demand_planning import Client, ok

OUT=e.ROOT/'docs/e2e/ortools-cpsat'

def strategy(c, mode='CANDIDATE', engine='ORTOOLS_CP_SAT', seconds=45):
    row=ok(c.call('/planning-strategy/page?current=1&size=10'))['records'][0]
    row['name']='离散装配｜CP-SAT独立约束求解'
    row['config'].update(solverEngine=engine, direction='FORWARD', materialConstrained=True,
        solverProfile='DEFAULT', randomSeed=17, scoreCalculationCountLimit=None,
        solveTimeSeconds=seconds, maxNoImprovementTime=0, adaptive={'enabled':False},
        cpSat=dict(executionMode=mode,numWorkers=1,horizonDays=14,useBaselineHint=True))
    ok(c.call('/planning-strategy',row,method='PUT'))
    return row

def pending():
    return int(e.sql("SELECT COUNT(*) FROM jvs_plan_task_pending WHERE tenant_id='demo'"))

def assert_persisted(tasks, table):
    assert table in ('jvs_plan_task_pending','jvs_plan_task')
    for t in tasks:
        code=t['taskCode'].replace("'","''")
        stored=e.sql("SELECT DATE_FORMAT(start_time,'%Y-%m-%dT%H:%i:%s.%f'),DATE_FORMAT(end_time,'%Y-%m-%dT%H:%i:%s.%f') FROM "+table+" WHERE tenant_id='demo' AND code='"+code+"'").split('\t')
        assert list(map(datetime.fromisoformat,stored))==[datetime.fromisoformat(t['start']),datetime.fromisoformat(t['end'])]

def generate(c,label,success=True,delta=None):
    body=dict(planningStrategyId='ps-demo-forward')
    if delta is not None: body['dynamicRequest']=delta
    ok(c.call('/smart-scheduling/generate',body))
    return e.await_solve(c,label,success)

def bridge(c):
    ok(c.call('/smart-scheduling/plan/pending/cancel',{}));e.fixture(6)
    strategy(c,engine='TIMEFOLD',seconds=45)
    base_audit,base,_=generate(c,'bridge-timefold');e.validate(base_audit,base,6)
    ok(c.call('/smart-scheduling/plan/pending/confirm',{}));strategy(c)
    audit,result,_=generate(c,'bridge-cpsat');e.validate(audit,result,6)
    cp=result['cpSat'];assert cp['baselineAccepted']
    assert cp['baselineCost']==-int(base['score'].split('/')[1].removesuffix('soft'))
    assert cp['worker']['cost']<=cp['baselineCost']
    e.record('confirmed Timefold baseline transfers to CP-SAT with identical cost and non-regression',
        auditId=audit['id'],timefoldCost=cp['baselineCost'],cpSatCost=cp['worker']['cost'])
    e.REPORT.update(status='PASS',candidateAudit=audit['id'],timefoldScore=base['score'],cpSatScore=result['score'])
    e.save('report',e.REPORT)

def remaining(c,base,result):
    first=min(result['tasks'],key=lambda x:(x['start'],x['taskCode']))
    ok(c.call('/task-report/update/progress',dict(taskCode=first['taskCode'],quantityCompleted=1,endTime=first['start'].replace('T',' '))))
    start=e.DAY+timedelta(days=1);end=start+timedelta(hours=4)
    delta=dict(freezeUntil=e.DAY.isoformat(),seeds=[],events=[
        dict(type='RESOURCE_UNAVAILABLE',targetId='r-assy01',start=start.isoformat(),end=end.isoformat()),
        dict(type='MATERIAL_DELAY',targetId='m-rm-copper',availableAt=start.isoformat()),
        dict(type='INSERT_ORDER',targetId='po-006')])
    audit,result,elapsed=generate(c,'dynamic',delta=delta);checks=e.validate(audit,result,6)
    assert checks['frozen']>=1 and checks['release']>=1
    for t in result['tasks']:
        if t['resourceId']=='r-assy01': assert not(t['start']<end.isoformat() and t['end']>start.isoformat())
    e.record('dynamic frozen WIP, downtime, material release and priority insertion',auditId=audit['id'],seconds=elapsed,**checks)
    ok(c.call('/smart-scheduling/plan/pending/cancel',{}))
    impossible=dict(freezeUntil=e.DAY.isoformat(),seeds=[],events=[dict(type='RESOURCE_UNAVAILABLE',targetId=first['resourceId'],start=first['start'],end=first['end'])])
    audit,result,_=generate(c,'infeasible-frozen',False,impossible);assert pending()==0
    e.record('infeasible frozen outage never publishes',auditId=audit['id'])
    ok(c.call('/smart-scheduling/plan/pending/cancel',{}))
    # Cancel a sufficiently large native search after its audit becomes visible.
    e.fixture(12);strategy(c,seconds=30)
    ok(c.call('/smart-scheduling/generate',dict(planningStrategyId='ps-demo-forward')))
    time.sleep(2)
    ok(c.call('/smart-scheduling/plan/pending/cancel',{}))
    for _ in range(80):
        status=e.sql("SELECT status FROM jvs_solve_audit WHERE tenant_id='demo' ORDER BY created_at DESC LIMIT 1")
        if status=='CANCELLED':break
        time.sleep(.25)
    assert status=='CANCELLED' and pending()==0,status
    e.record('native worker cancellation leaves no candidate')
    # Actual incoming supply: no opening steel; one reserved arrival is later than the plan start.
    e.fixture(6);strategy(c)
    arrival=(e.DAY+timedelta(days=1)).replace(hour=9)
    e.sql("UPDATE jvs_material SET quantity=0 WHERE id='m-rm-steel' AND tenant_id='demo';"
          f"UPDATE jvs_incoming_material_order SET quantity=100000,delivery_time='{arrival}' WHERE id='imo-001' AND tenant_id='demo';")
    audit,result,_=generate(c,'inbound-reservation');assert result['cpSat']['verified']
    releases=[t['notBefore'] for t in json.loads(audit['inputJson'])['problem']['tasks'] if t.get('notBefore')]
    assert releases and all(x>=arrival.isoformat() for x in releases)
    e.record('quantity-reserved inbound arrival becomes hard release',auditId=audit['id'],releasedTasks=len(releases))
    ok(c.call('/smart-scheduling/plan/pending/cancel',{}))
    e.sql("UPDATE jvs_incoming_material_order SET quantity=1 WHERE id='imo-001' AND tenant_id='demo';")
    audit,result,_=generate(c,'uncovered-supply',False);assert pending()==0
    e.record('uncovered purchased supply fails closed',auditId=audit['id'])
    # Leave a verified candidate for interactive configuration, audit and Gantt screenshots.
    ok(c.call('/smart-scheduling/plan/pending/cancel',{}));e.fixture(6);strategy(c)
    audit,result,_=generate(c,'screenshot-candidate');e.validate(audit,result,6)
    e.REPORT.update(status='RUNNING',candidateAudit=audit['id'],timefoldScore=base['score'],cpSatScore=result['score'])
    e.save('report',e.REPORT)
    bridge(c)
    print('COMPLETE',OUT,flush=True)

def main():
    p=argparse.ArgumentParser();p.add_argument('--disposable',action='store_true',required=True)
    p.add_argument('--bridge-only',action='store_true',help='Finish the cross-engine baseline check after a successful full run')
    p.add_argument('--resume-dynamic',action='store_true',help='Resume only after the verified candidate has been confirmed')
    args=p.parse_args()
    e.OUT=OUT;e.REPORT=dict(synthetic=True,status='RUNNING',planStart=e.DAY.isoformat(),cases=[])
    assert e.sql('SELECT DATABASE()')=='aps_e2e'
    e.sql((e.ROOT/'aps/sql/20261125_cpsat_time_precision.sql').read_text())
    c=Client(e.BASE)
    for attempt in range(30):
        try: ok(c.call('/health'));break
        except Exception:
            if attempt==29:raise
            time.sleep(1)
    c.login()
    if args.resume_dynamic:
        e.REPORT=json.loads((OUT/'report.json').read_text())
        assert e.REPORT['planStart']==e.DAY.isoformat() and len(e.REPORT['cases'])>=6
        base=json.loads(json.loads((OUT/'timefold-baseline.json').read_text())['audit']['resultJson'])
        result=json.loads(json.loads((OUT/'candidate.json').read_text())['audit']['resultJson'])
        ok(c.call('/smart-scheduling/plan/pending/cancel',{}))
        remaining(c,base,result);return
    if args.bridge_only:
        e.REPORT=json.loads((OUT/'report.json').read_text());assert e.REPORT['status']=='PASS'
        bridge(c);return
    ok(c.call('/smart-scheduling/plan/pending/cancel',{}));e.fixture(6)
    # All quantities, dates and equipment in this acceptance dataset are synthetic.
    strategy(c,engine='TIMEFOLD')
    audit,base,elapsed=generate(c,'timefold-baseline');e.validate(audit,base,6)
    e.record('existing Timefold HTTP regression',score=base['score'],seconds=elapsed)
    ok(c.call('/smart-scheduling/plan/pending/cancel',{}))
    strategy(c,mode='SHADOW')
    audit,result,elapsed=generate(c,'shadow');e.validate(audit,result,6)
    assert audit['status']=='SHADOW_FEASIBLE' and pending()==0
    assert result['cpSat']['verified'] and result['cpSat']['worker']['status'] in ('FEASIBLE','OPTIMAL')
    e.record('shadow independently verified without candidate publication',auditId=audit['id'],score=result['score'],seconds=elapsed)
    ok(c.call('/smart-scheduling/plan/pending/cancel',{}));strategy(c)
    audit,result,elapsed=generate(c,'candidate');checks=e.validate(audit,result,6);assert pending()==36
    assert_persisted(result['tasks'],'jvs_plan_task_pending')
    for endpoint in ('tasks','resource/gantt','order/gantt','material/gantt'):
        preview=ok(c.call('/smart-scheduling/preview/plan/'+endpoint,{}));assert preview;e.save('preview-'+endpoint.replace('/','-'),preview)
    e.record('CP-SAT candidate and four real previews',auditId=audit['id'],seconds=elapsed,score=result['score'],**checks)
    other=Client(e.BASE);other.login('other','other-admin','e2e-only')
    assert ok(other.call('/smart-scheduling/audit/'+audit['id'])) is None
    low=Client(e.BASE);low.login('demo','no-permission','e2e-only')
    assert low.call('/smart-scheduling/generate',dict(planningStrategyId='ps-demo-forward'))['code']==403
    e.record('tenant isolation and run permission gate')
    e.sql("UPDATE jvs_material SET quantity=quantity+1 WHERE id='m-rm-steel' AND tenant_id='demo'")
    rejection=c.call('/smart-scheduling/plan/pending/confirm',{});assert rejection['code']!=0;e.save('stale-rejection',rejection)
    e.sql("UPDATE jvs_material SET quantity=quantity-1 WHERE id='m-rm-steel' AND tenant_id='demo'")
    ok(c.call('/smart-scheduling/plan/pending/confirm',{}))
    assert ok(c.call('/smart-scheduling/audit/'+audit['id']))['status']=='CONFIRMED'
    assert_persisted(result['tasks'],'jvs_plan_task')
    e.record('stale source blocks confirmation; unchanged source confirms atomically')
    baseline_audit,protected,_=generate(c,'baseline-protection')
    assert protected['cpSat']['baselineAccepted'],protected['cpSat'].keys()
    assert protected['cpSat']['worker']['cost']<=protected['cpSat']['baselineCost']
    e.record('confirmed feasible baseline is independently revalidated and cannot regress',auditId=baseline_audit['id'])
    ok(c.call('/smart-scheduling/plan/pending/cancel',{}))
    remaining(c,base,result)

if __name__=='__main__':
    try: main()
    except Exception as failure:
        e.REPORT.update(status='FAILED',failure=str(failure))
        e.save('report',e.REPORT)
        raise

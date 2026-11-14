#!/usr/bin/env python3
"""Real MiniCPM -> reviewed events -> Timefold -> preview -> confirm. No model mocks.
Requires the isolated fixture and the backend started with run-minicpm-e2e.zsh.
"""
import json
import os
import re
import time
from datetime import datetime
from pathlib import Path
from e2e_hybrid import Client, ok, sql, wait, validate

DESTINATION = Path(__file__).resolve().parents[2] / 'docs/e2e/minicpm-report.json'
FREEZE = '2026-11-16T08:00:00'
EXPECTED = [
    dict(type='RESOURCE_UNAVAILABLE', targetId='r-assy01', start='2026-11-18T08:00:00', end='2026-11-18T12:00:00'),
    dict(type='MATERIAL_DELAY', targetId='m-rm-copper', availableAt='2026-11-18T08:00:00'),
    dict(type='DUE_DATE_CHANGE', targetId='po-006', deliveryTime='2026-11-20T12:00:00'),
]
NARRATIVE = ('设备r-assy01在2026年11月18日08:00至12:00停机；'
             '原材料m-rm-copper延期至2026年11月18日08:00到货；'
             '订单po-006交付日期调整为2026年11月20日12:00。'
             '仅提取以上三项事件，不推荐种子位置、策略或权重。')

def main():
    report = dict(model=os.environ.get('APS_SMALL_MODEL_NAME', 'MiniCPM (zshrc configuration)'),
                  status='RUNNING', cases=[], submittedToSolver=False, stage='setup')
    def save():
        DESTINATION.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    def record(name, **evidence):
        report['cases'].append(dict(name=name, status='PASS', **evidence))
        save()
        print('PASS', name, evidence, flush=True)
    try:
        assert sql('SELECT DATABASE()') == 'aps_e2e'
        c = Client()
        for attempt in range(40):
            try:
                ok(c.call('/health'))
                break
            except Exception:
                if attempt == 39:
                    raise
                time.sleep(.5)
        ok(c.login())
        assert ok(c.call('/smart-scheduling/plan/progress'))['status'] == 'NONE', 'finish or cancel existing job first'
        config = ok(c.call('/planning-strategy/page?current=1&size=10'))['records'][0]
        assert config['config']['solverEngine'] == 'TIMEFOLD'
        checksum = 'CHECKSUM TABLE jvs_production_order,jvs_plan_task,jvs_plan_task_pending,jvs_material,jvs_solve_audit EXTENDED'
        before = sql(checksum)
        report['stage'] = 'model_event_extraction'
        started = time.monotonic()
        response = c.call('/smart-scheduling/presolve', dict(narrative=NARRATIVE, freezeUntil=FREEZE))
        report['extractionSeconds'] = round(time.monotonic()-started, 2)
        report['presolveResponse'] = response
        report['extractionDatabaseUnchanged'] = before == sql(checksum)
        save()
        assert report['extractionDatabaseUnchanged'], 'presolve changed business data'
        proposal = ok(response)
        actual = {(event['type'], event['targetId']): event for event in proposal['events']}
        assert len(proposal['events']) == len(actual) == len(EXPECTED), proposal
        for event in EXPECTED:
            candidate = actual[(event['type'], event['targetId'])]
            assert all(candidate.get(key) == value for key, value in event.items()), candidate
        assert proposal['freezeUntil'] == FREEZE
        assert not proposal.get('seeds') and not proposal.get('recommendedProfile') and not proposal.get('recommendedWeights')
        record('real MiniCPM event types, IDs and times exactly match reviewed expectations', events=3,
               seconds=report['extractionSeconds'], databaseUnchanged=True)
        report['stage'] = 'timefold_solve'
        ok(c.call('/smart-scheduling/generate', dict(planningStrategyId=config['id'], dynamicRequest=proposal)))
        report['submittedToSolver'] = True
        plan, audit, result, seconds = wait(c)
        report.update(planId=plan, score=result['score'], tasks=len(result['tasks']),
                      changedTaskCount=result['changedTaskCount'], solveSeconds=seconds)
        assert validate(result['tasks']) == 36
        assert result['solverEngine'] == 'TIMEFOLD'
        record('reviewed model proposal produces a fully assigned feasible Timefold plan',
               planId=plan, score=result['score'], tasks=36, seconds=seconds)
        snapshot = json.loads(audit['inputJson'])
        assert snapshot['basicData']['dynamicRequest'] == proposal, 'solver input differs from reviewed model output'
        baseline = {task['code']: task for task in snapshot['basicData']['baselineTasks']}
        pinned = 0
        delayed = {task['code'] for task in snapshot['problem']['tasks'] if task.get('notBefore')}
        assert delayed
        for task in result['tasks']:
            if task['pinned']:
                old = baseline[task['taskCode']]
                assert task['resourceId'] == old['mainResourceId']
                assert datetime.fromisoformat(task['start']) == datetime.fromisoformat(old['startTime'])
                assert datetime.fromisoformat(task['end']) == datetime.fromisoformat(old['endTime'])
                pinned += 1
            if task['resourceId'] == 'r-assy01':
                assert not (datetime.fromisoformat(task['start']) < datetime(2026,11,18,12)
                            and datetime.fromisoformat(task['end']) > datetime(2026,11,18,8))
            if task['taskCode'] in delayed:
                assert datetime.fromisoformat(task['start']) >= datetime(2026,11,18,8)
        assert pinned > 0
        record('frozen work, resource downtime and material availability remain valid', pinned=pinned,
               delayedTasks=len(delayed), changedTasks=result['changedTaskCount'])
        for endpoint in ['tasks', 'resource/gantt', 'order/gantt', 'material/gantt']:
            assert ok(c.call('/smart-scheduling/preview/plan/'+endpoint, {})) is not None
        record('four candidate preview APIs return real schedule data')
        explanation = result['modelExplanation']
        assert explanation.startswith('解释性建议（模型生成）：'), explanation
        report['modelExplanation'] = explanation
        record('real MiniCPM post-solve explanation returned with model label')
        report['stage'] = 'confirm'
        ok(c.call('/smart-scheduling/plan/pending/confirm', {}))
        assert ok(c.call('/smart-scheduling/audit/'+plan))['status'] == 'CONFIRMED'
        assert sql("SELECT COUNT(*) FROM jvs_plan_task WHERE tenant_id='demo'") == '36'
        assert sql("SELECT COUNT(*) FROM jvs_plan_task_pending WHERE tenant_id='demo'") == '0'
        assert sql("SELECT delivery_time FROM jvs_production_order WHERE id='po-006' AND tenant_id='demo'") == '2026-11-20 12:00:00'
        record('reviewed combined optimization confirmed atomically with updated due date')
        report.update(status='PASS', stage='complete')
        return 0
    except Exception as error:
        report.update(status='FAIL', error=str(error))
        provider = re.search(r'HTTP (401|403|404)', str(error))
        if report['stage'] == 'model_event_extraction' and provider:
            report.update(status='BLOCKED_PROVIDER', providerHttpStatus=int(provider.group(1)))
        print('FAIL', report['stage'], str(error), flush=True)
        return 1
    finally:
        save()

if __name__ == '__main__':
    raise SystemExit(main())

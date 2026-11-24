#!/usr/bin/env python3
"""Destructive synthetic acceptance, ONLY the dedicated aps-ade-e2e-mysql container.
Real HTTP -> application -> MySQL/Redis -> Timefold. No solver mocks.
"""
import argparse
import json
import re
import subprocess
import time
import traceback
import urllib.error
from datetime import datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo
from concurrent.futures import ThreadPoolExecutor
from e2e_demand_planning import Client, ok

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/e2e/adaptive'
BASE = 'http://127.0.0.1:31010/mgr/jvs-aps'
MYSQL = ['docker', 'exec', '-i', '-e', 'MYSQL_PWD=ade-local-only',
         'aps-ade-e2e-mysql', 'mysql', '-uroot', '-N', '-B', 'aps_e2e']
DAY = (datetime.now(ZoneInfo('Asia/Shanghai')) + timedelta(days=1)).replace(hour=8, minute=0, second=0, microsecond=0, tzinfo=None)
REPORT = {'synthetic': True, 'status': 'RUNNING', 'planStart': DAY.isoformat(),
          'description': 'Synthetic forklift E20/E30; 6 operations/order; 8 resources; no customer data',
          'cases': []}


def sql(statement):
    return subprocess.check_output(MYSQL, input=statement, text=True).strip()


def save(name, value):
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / (name + '.json')).write_text(json.dumps(value, ensure_ascii=False, indent=2))


def record(name, **evidence):
    REPORT['cases'] = [c for c in REPORT['cases'] if c['name'] != name]
    REPORT['cases'].append(dict(name=name, status='PASS', **evidence))
    save('report', REPORT)
    print('PASS', name, json.dumps(evidence, ensure_ascii=False), flush=True)


def fixture(count):
    assert sql('SELECT DATABASE()') == 'aps_e2e'
    seed = (ROOT / 'demo-seed.sql').read_text().replace('USE `jvs-aps`;', 'USE `aps_e2e`;')
    seed = seed.replace("SET @tenant := '1';", "SET @tenant := 'demo';")
    for day in range(16, 22):
        seed = seed.replace(f'2026-04-{day:02}', (DAY + timedelta(days=day-16)).strftime('%Y-%m-%d'))
    seed = seed.replace('2026-12-31', f'{DAY.year+1}-12-31')
    for old, new in [('变频风扇总成A','合成叉车 E20'), ('变频风扇总成B','合成叉车 E30'),
                     ('风扇机壳半成品','合成叉车车架'), ('风扇转子半成品','合成液压模块'),
                     ('高纯铜线','合成液压关键件'), ('转子绕线','液压模块预装')]:
        seed = seed.replace(old, new)
    for a, b in [('演示工厂正排策略', '合成叉车｜多层硬约束自适应'), ('机壳', '车架'),
                 ('转子', '液压模块'), ('绕线', '液压预装'), ('风机', '合成叉车'),
                 ('铜线', '液压关键件'), ('轴承', '轮系轴承')]:
        seed = seed.replace(a, b)
    cleanup = '\n'.join("DELETE FROM " + t + " WHERE tenant_id='demo';" for t in [
        'jvs_plan_task_pending', 'jvs_plan_task_order_pending', 'jvs_plan_task_adjust',
        'jvs_plan_task_order', 'jvs_plan_task', 'jvs_plan_info', 'jvs_production_order'])
    rows = []
    for i in range(1, count+1):
        due = (DAY + timedelta(days=2 + i//8)).replace(hour=18).strftime('%Y-%m-%d %H:%M:%S')
        material = 'FG-FAN-B' if i % 3 == 0 else 'FG-FAN-A'
        rows.append(f"('po-{i:03}','SYN-FL-{i:03}','{material}',{10+i%6*2},'{due}',{1+i%5},{i},"
                    "'MANUFACTURE','PENDING','UNSCHEDULED','#8EC5FC',1,0,0,'synthetic','synthetic',NOW(),'synthetic',NOW(),'demo')")
    tail = cleanup + '\n'
    # Explicit IDs avoid relying on material enum spelling.
    tail += "UPDATE jvs_material SET quantity=0 WHERE tenant_id='demo' AND id IN ('m-sf-housing','m-sf-rotor','m-fg-fan-a','m-fg-fan-b');\n"
    tail += "INSERT INTO jvs_production_order (id,code,material_code,quantity,delivery_time,priority,sequence,type,order_status,scheduling_status,color,can_schedule,supplement,del_flag,create_by,create_by_id,create_time,update_by,update_time,tenant_id) VALUES " + ',\n'.join(rows) + ';\n'
    OUT.mkdir(parents=True, exist_ok=True)
    statement = cleanup + '\n' + seed + '\n' + tail
    (OUT / f'fixture-{count}.sql').write_text(statement)
    sql(statement)


def strategy(c, adaptive, count=6, constrained=False):
    trial_seconds, total_seconds, scores = (5, 45, 2000) if count<=6 or constrained else ((20, 165, 8000) if count<=12 else (240, 500, 22000))
    row = ok(c.call('/planning-strategy/page?current=1&size=10'))['records'][0]
    row['config'].update(solverEngine='TIMEFOLD', materialConstrained=True, randomSeed=17,
        solverProfile='FFD_LATE_ACCEPTANCE', scoreCalculationCountLimit=16000,
        solveTimeSeconds=45, maxNoImprovementTime=0,
        adaptive=dict(enabled=adaptive, populationSize=4, generations=1, totalSeconds=total_seconds,
                      trialSeconds=trial_seconds, trialScoreLimit=scores,
                      warmStartEnabled=True, budgetMode='FIXED' if constrained else 'SCALE_AWARE'))
    ok(c.call('/planning-strategy', row, method='PUT'))
    actual = ok(c.call('/planning-strategy/page?current=1&size=10'))['records'][0]['config']
    assert actual.get('adaptive', {}).get('enabled') == adaptive, 'Running backend is stale or dropped adaptive config'


def await_solve(c, label, success=True):
    began = time.monotonic()
    while time.monotonic()-began < 560:
        p = ok(c.call('/smart-scheduling/plan/progress'))
        if p['status'] in ('SUCCESS', 'FAILURE', 'NO_SCHEDULED'):
            ids = [re.search(r'[a-f0-9-]{36}', x).group() for x in p['contents'] if '审计ID' in x]
            assert ids, p
            audit = ok(c.call('/smart-scheduling/audit/' + ids[-1]))
            save(label, dict(progress=p, audit=audit))
            if success is not None:
                assert (p['status'] == 'SUCCESS') == success, p
            return audit, json.loads(audit['resultJson']), round(time.monotonic()-began, 3)
        time.sleep(.25)
    raise AssertionError('HTTP solve timeout: ' + label)


def validate(audit, result, count):
    assert result['score'].startswith('0hard/'), result['score']
    rows = result['tasks']
    assert len(rows) == count*6, len(rows)
    by_code = {t['taskCode']: t for t in rows}
    assert len(by_code) == len(rows)
    problem = json.loads(audit['inputJson'])['problem']
    resources = {r['id']: r for r in problem['resources']}
    assert set(by_code) == {t['code'] for t in problem['tasks']}
    groups = {}
    checks = dict(tasks=len(rows), precedence=0, release=0, frozen=0, capability=0, calendar=0, duration=0)
    for task in problem['tasks']:
        row = by_code[task['code']]
        start, end = map(datetime.fromisoformat, (row['start'], row['end']))
        assert start < end and start >= DAY
        resource = resources[row['resourceId']]
        assert row['resourceId'] in {r['id'] for r in task['process']['useMainResources']}
        checks['capability'] += 1
        # Operations may span a lunch break/night; their productive boundaries must be on shift.
        for instant, is_end in ((start, False), (end, True)):
            valid = False
            for calendar in resource['workCalendars']:
                if not (datetime.fromisoformat(calendar['beginTime']) <= instant <= datetime.fromisoformat(calendar['endTime'])):
                    continue
                if not calendar['workDaySetting'].get(instant.strftime('%A').upper()):
                    continue
                clock = instant.strftime('%H:%M:%S')
                valid |= any(w['start'] < clock <= w['end'] if is_end else w['start'] <= clock < w['end']
                             for w in calendar['workModeTimes'])
            assert valid, (row['taskCode'], instant, resource['id'])
        checks['calendar'] += 1
        if not task.get('frozenResourceId'):
            # Independent integration over this fixture's explicit weekday shifts.
            assert len(resource['workCalendars']) == 1
            calendar = resource['workCalendars'][0]
            day = start.replace(hour=0, minute=0, second=0)
            productive = 0
            while day.date() <= end.date():
                if calendar['workDaySetting'].get(day.strftime('%A').upper()):
                    for window in calendar['workModeTimes']:
                        a = datetime.fromisoformat(day.date().isoformat()+'T'+window['start'])
                        b = datetime.fromisoformat(day.date().isoformat()+'T'+window['end'])
                        productive += max(0, (min(end,b)-max(start,a)).total_seconds())
                day += timedelta(days=1)
            throughput = next(r['throughput'] for r in task['process']['useMainResources'] if r['id']==row['resourceId'])
            assert throughput.endswith('pH')
            expected = float(task['quantity'])*3600/float(throughput[:-2])
            # Existing engine rounds hours to two decimal places: maximum deviation is 18 seconds.
            assert abs(productive-expected) <= 18.001, (row['taskCode'],productive,expected)
            checks['duration'] += 1
        groups.setdefault(row['resourceId'], []).append((start, end))
        for predecessor in task.get('frontTaskCodes') or []:
            assert datetime.fromisoformat(by_code[predecessor]['end']) <= start
            checks['precedence'] += 1
        if task.get('notBefore'):
            assert start >= datetime.fromisoformat(task['notBefore'])
            checks['release'] += 1
        if task.get('frozenResourceId'):
            assert row['resourceId'] == task['frozenResourceId']
            assert start == datetime.fromisoformat(task['frozenStartTime'])
            assert end == datetime.fromisoformat(task['frozenEndTime'])
            checks['frozen'] += 1
    for intervals in groups.values():
        intervals.sort()
        assert all(a[1] <= b[0] for a, b in zip(intervals, intervals[1:]))
    adaptive = result.get('adaptive')
    assert bool(adaptive) == bool(problem['strategy']['config'].get('adaptive', {}).get('enabled'))
    if adaptive:
        trials = adaptive['trials']
        assert 1 <= len(trials) <= 8
        assert all(t['status'] in ('FEASIBLE', 'INFEASIBLE', 'INVALID_FROZEN') for t in trials), trials
        assert any(t['index'] == adaptive['selectedTrial'] and t['score'] == result['score'] for t in trials)
        feasible_scores = [int(t['score'].split('/')[1].removesuffix('soft')) for t in trials if t['status']=='FEASIBLE']
        assert int(result['score'].split('/')[1].removesuffix('soft')) == max(feasible_scores)
        if adaptive['algorithm'] == 'JDE_TIMEFOLD_V2':
            assert adaptive['budget']['activeTasks'] == len(rows)
            for trial in trials:
                execution = trial['execution']
                assert execution['budgetMillis'] > 0 and execution['scoreLimit'] >= 10
                if execution['startMode'] == 'WARM':
                    assert not execution['constructionEnabled'] and not execution['fallbackReason']
                    source = trials[execution['seedTrial']]
                    assert source['index'] < trial['index'] and source['status'] == 'FEASIBLE'
                    assert source['score'] == execution['seedScore']
        checks.update(trials=len(trials), selected=adaptive['selectedTrial'], stop=adaptive['stopReason'])
    return checks


def main():
    global OUT
    parser = argparse.ArgumentParser()
    parser.add_argument('--scales', default='6,12,24', help='Comma-separated order counts; empty skips scale runs')
    parser.add_argument('--resume', action='store_true', help='Reuse successful scale evidence from this plan date')
    parser.add_argument('--output', type=Path, default=OUT, help='Evidence directory; use a new directory to retain prior acceptance')
    args = parser.parse_args()
    OUT = args.output.resolve()
    if args.resume:
        prior = json.loads((OUT/'report.json').read_text())
        assert prior['planStart'] == DAY.isoformat()
        REPORT['cases'] = prior['cases']
    c = Client(BASE)
    for attempt in range(60):
        try:
            ok(c.call('/health'))
            break
        except urllib.error.URLError:
            if attempt == 59:
                raise
            time.sleep(1)
    c.login()
    other = Client(BASE); other.login('other', 'other-admin', 'e2e-only')
    low = Client(BASE); low.login('demo', 'no-permission', 'e2e-only')
    assert low.call('/smart-scheduling/generate', {'planningStrategyId':'ps-demo-forward'})['code'] == 403
    assert Client(BASE).call('/planning-strategy/page')['code'] != 0
    record('authentication and scheduling authorization')
    for count in (int(n) for n in args.scales.split(',') if n):
        ok(c.call('/smart-scheduling/plan/pending/cancel', {}))
        fixture(count)
        for adaptive in (False, True):
            label = f'scale-{count}-' + ('adaptive' if adaptive else 'timefold')
            if args.resume and any(x['name']==label and x['status']=='PASS' for x in REPORT['cases']):
                continue
            strategy(c, adaptive, count)
            ok(c.call('/smart-scheduling/generate', {'planningStrategyId':'ps-demo-forward'}))
            audit, result, elapsed = await_solve(c, label)
            checks = validate(audit, result, count)
            assert ok(other.call('/smart-scheduling/audit/'+audit['id'])) is None
            record(label, auditId=audit['id'], score=result['score'], seconds=elapsed, **checks)
            ok(c.call('/smart-scheduling/plan/pending/cancel', {}))
    if not args.resume or not any(x['name']=='360-task undersized budget safely rejected' for x in REPORT['cases']):
        fixture(60); strategy(c, True, 60, constrained=True)
        ok(c.call('/smart-scheduling/generate', {'planningStrategyId':'ps-demo-forward'}))
        audit, result, elapsed = await_solve(c, 'scale-60-insufficient-budget', success=False)
        assert result.get('adaptive') and result['adaptive']['selectedTrial'] == -1
        assert sql("SELECT COUNT(*) FROM jvs_plan_task_pending WHERE tenant_id='demo'") == '0'
        record('360-task undersized budget safely rejected', auditId=audit['id'], seconds=elapsed,
               capacityConclusion='No feasible schedule under 5s/trial and 2000 score calculations; not a scalability pass')
    ok(c.call('/smart-scheduling/plan/pending/cancel', {}))
    fixture(6); strategy(c, True)
    with ThreadPoolExecutor(2) as pool:
        responses = list(pool.map(lambda _: c.call('/smart-scheduling/generate', {'planningStrategyId':'ps-demo-forward'}), range(2)))
    assert sum(r['code'] == 0 for r in responses) == 1, responses
    audit, result, elapsed = await_solve(c, 'baseline')
    validate(audit, result, 6)
    record('concurrent submission admits exactly one solve')
    sql("UPDATE jvs_material SET quantity=quantity+1 WHERE id='m-rm-steel' AND tenant_id='demo';")
    try:
        rejected = c.call('/smart-scheduling/plan/pending/confirm', {})
        assert rejected['code'] != 0 and '数据已变化' in rejected['msg'], rejected
    finally:
        sql("UPDATE jvs_material SET quantity=quantity-1 WHERE id='m-rm-steel' AND tenant_id='demo';")
    record('stale snapshot confirmation rejected')
    ok(c.call('/smart-scheduling/plan/pending/confirm', {}))
    first = min(result['tasks'], key=lambda t: (t['start'], t['taskCode']))
    ok(c.call('/task-report/update/progress', dict(taskCode=first['taskCode'], quantityCompleted=1, endTime=first['start'].replace('T',' '))))
    outage_start = DAY + timedelta(days=1)
    outage_end = outage_start + timedelta(hours=4)
    delta = dict(freezeUntil=DAY.isoformat(), seeds=[], events=[
        dict(type='RESOURCE_UNAVAILABLE', targetId='r-assy01', start=outage_start.isoformat(), end=outage_end.isoformat()),
        dict(type='MATERIAL_DELAY', targetId='m-rm-copper', availableAt=outage_start.isoformat()),
        dict(type='INSERT_ORDER', targetId='po-006'),
        dict(type='DUE_DATE_CHANGE', targetId='po-006', deliveryTime=(DAY+timedelta(days=2,hours=10)).isoformat())])
    save('dynamic-request', delta)
    ok(c.call('/smart-scheduling/generate', dict(planningStrategyId='ps-demo-forward', dynamicRequest=delta)))
    audit, result, elapsed = await_solve(c, 'dynamic')
    checks = validate(audit, result, 6)
    assert checks['frozen'] >= 1 and checks['release'] >= 1
    if result.get('adaptive', {}).get('algorithm') == 'JDE_TIMEFOLD_V2':
        assert any(t['execution']['startMode']=='WARM' for t in result['adaptive']['trials']), 'Dynamic frozen scenario must exercise warm start'
    for t in result['tasks']:
        if t['resourceId'] == 'r-assy01':
            assert not (datetime.fromisoformat(t['start']) < outage_end and datetime.fromisoformat(t['end']) > outage_start)
    for endpoint in ('tasks', 'resource/gantt', 'order/gantt', 'material/gantt'):
        data = ok(c.call('/smart-scheduling/preview/plan/'+endpoint, {}))
        assert data
        save('preview-'+endpoint.replace('/', '-'), data)
    record('dynamic outage, material delay, priority insertion, frozen WIP and four previews',
           auditId=audit['id'], score=result['score'], seconds=elapsed, **checks)
    ok(c.call('/smart-scheduling/plan/pending/confirm', {}))
    assert ok(c.call('/smart-scheduling/audit/'+audit['id']))['status'] == 'CONFIRMED'
    assert sql("SELECT COUNT(*) FROM jvs_plan_task WHERE tenant_id='demo'") == '36'
    assert sql("SELECT delivery_time FROM jvs_production_order WHERE tenant_id='demo' AND id='po-006'") == (DAY+timedelta(days=2,hours=10)).strftime('%Y-%m-%d %H:%M:%S')
    confirmed_query = "SELECT code,start_time,end_time,main_resource_id FROM jvs_plan_task WHERE tenant_id='demo' ORDER BY code"
    confirmed_tasks = sql(confirmed_query)
    save('confirmed-tasks', confirmed_tasks.splitlines())
    record('atomic confirmation persists 36 tasks and terminal audit')
    ok(c.call('/smart-scheduling/generate', {'planningStrategyId':'ps-demo-forward'}))
    deadline = time.monotonic()+30
    running_id = None
    while time.monotonic() < deadline:
        progress = ok(c.call('/smart-scheduling/plan/progress'))
        ids = [re.search(r'[a-f0-9-]{36}', x).group() for x in progress.get('contents', []) if '审计ID' in x]
        if ids:
            running = ok(c.call('/smart-scheduling/audit/'+ids[-1]))
            partial = json.loads(running.get('resultJson') or '{}')
            completed = partial.get('adaptive', {}).get('trials', [])
            warm_observed = any(t.get('execution', {}).get('startMode') == 'WARM' for t in completed)
            if running['status'] == 'RUNNING' and completed and (
                    partial['adaptive'].get('algorithm') != 'JDE_TIMEFOLD_V2' or warm_observed):
                running_id = ids[-1]
                save('cancel-before', running)
                break
        time.sleep(.1)
    assert running_id, 'Cancellation must exercise an active solver after a real trial'
    ok(c.call('/smart-scheduling/plan/pending/cancel', {}))
    deadline = time.monotonic()+60
    while time.monotonic() < deadline:
        active = subprocess.check_output(['docker','exec','-e','REDISCLI_AUTH=ade-local-only',
            'aps-ade-e2e-redis','redis-cli','-n','2','--scan','--pattern','*smart:planning:generate:planId:demo'], text=True).strip()
        if not active: break
        time.sleep(.25)
    assert not active
    assert sql("SELECT COUNT(*) FROM jvs_plan_task_pending WHERE tenant_id='demo'") == '0'
    assert sql(confirmed_query) == confirmed_tasks
    terminal = ok(c.call('/smart-scheduling/audit/'+running_id))
    save('cancel-after', terminal)
    assert terminal['status'] == 'CANCELLED', terminal
    assert json.loads(terminal['resultJson'])['adaptive']['trials']
    record('cancellation releases lock and never republishes pending tasks', auditId=running_id)
    impossible = dict(freezeUntil=DAY.isoformat(), seeds=[], events=[dict(type='RESOURCE_UNAVAILABLE',
        targetId=first['resourceId'], start=first['start'], end=first['end'])])
    ok(c.call('/smart-scheduling/generate', dict(planningStrategyId='ps-demo-forward', dynamicRequest=impossible)))
    audit, result, elapsed = await_solve(c, 'infeasible-frozen-outage', success=False)
    assert sql("SELECT COUNT(*) FROM jvs_plan_task_pending WHERE tenant_id='demo'") == '0'
    assert sql("SELECT COUNT(*) FROM jvs_plan_task WHERE tenant_id='demo'") == '36'
    assert sql(confirmed_query) == confirmed_tasks
    record('infeasible frozen outage rejected without replacing confirmed plan', auditId=audit['id'])
    ok(c.call('/smart-scheduling/plan/pending/cancel', {}))
    REPORT['status'] = 'PASS'; save('report', REPORT)


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        REPORT.update(status='FAIL', error=str(error)); save('report', REPORT)
        traceback.print_exc(); raise

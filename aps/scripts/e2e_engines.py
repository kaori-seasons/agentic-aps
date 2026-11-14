#!/usr/bin/env python3
"""Run after e2e_hybrid.py against the same disposable fixture."""
import json
from pathlib import Path
from e2e_hybrid import Client, ok, wait, validate, sql

def main():
    assert sql('SELECT DATABASE()') == 'aps_e2e'
    c = Client()
    ok(c.login())
    original = ok(c.call('/planning-strategy/page?current=1&size=10'))['records'][0]
    report = {'cases': [], 'status': 'FAIL'}
    try:
        for engine, direction in [('OPTAPLANNER', 'FORWARD'), ('TIMEFOLD', 'BACKWARD')]:
            row = json.loads(json.dumps(original))
            row['config'].update(solverEngine=engine, direction=direction)
            ok(c.call('/planning-strategy', row, method='PUT'))
            ok(c.call('/smart-scheduling/generate', {'planningStrategyId': row['id']}))
            plan, audit, result, seconds = wait(c)
            assert validate(result['tasks']) == 36
            baseline = {task['code']: task for task in json.loads(audit['inputJson'])['basicData']['baselineTasks']}
            for task in result['tasks']:
                if task['pinned']:
                    old = baseline[task['taskCode']]
                    assert task['resourceId'] == old['mainResourceId']
                    assert task['start'] == old['startTime']
                    assert task['end'] == old['endTime']
            evidence = dict(engine=engine, direction=direction, planId=plan,
                            score=result['score'], tasks=len(result['tasks']), seconds=seconds, status='PASS')
            report['cases'].append(evidence)
            print(evidence, flush=True)
            ok(c.call('/smart-scheduling/plan/pending/cancel', {}))
        report['status'] = 'PASS'
    finally:
        ok(c.call('/smart-scheduling/plan/pending/cancel', {}))
        ok(c.call('/planning-strategy', original, method='PUT'))
        destination = Path(__file__).resolve().parents[2] / 'docs/e2e/compatibility-report.json'
        destination.write_text(json.dumps(report, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Real-model semantic quality gate; intentionally exits nonzero if extraction is wrong.
Run after the other suites, with the model-enabled backend and no concurrent writes.
"""
import json
import time
from pathlib import Path
from e2e_hybrid import Client, ok, sql

def main():
    assert sql('SELECT DATABASE()') == 'aps_e2e'
    c = Client()
    ok(c.login())
    checksum = 'CHECKSUM TABLE jvs_production_order,jvs_plan_task,jvs_plan_task_pending,jvs_material,jvs_solve_audit EXTENDED'
    before = sql(checksum)
    expected = dict(type='RESOURCE_UNAVAILABLE', targetId='r-assy01',
                    start='2026-11-17T08:00:00', end='2026-11-17T12:00:00')
    samples = [
        ('natural', '资源r-assy01从2026-11-17T08:00:00到2026-11-17T12:00:00不可用。只提取这一条设备停机事件。'),
        ('explicit', '请逐字提取如下事件，不生成建议。type=RESOURCE_UNAVAILABLE，targetId=r-assy01，start=2026-11-17T08:00:00，end=2026-11-17T12:00:00。seeds为空数组。不要输出其他字段。'),
    ]
    cases = []
    for label, narrative in samples:
        started = time.monotonic()
        response = c.call('/smart-scheduling/presolve', dict(narrative=narrative, freezeUntil='2026-11-16T08:00:00'))
        data = response.get('data') or {}
        events = data.get('events') or []
        match = response.get('code') == 0 and len(events) == 1 and all(events[0].get(k) == v for k, v in expected.items())
        match = match and not data.get('seeds') and not data.get('recommendedProfile') and not data.get('recommendedWeights')
        evidence = dict(case=label, seconds=round(time.monotonic()-started, 2), semanticMatch=match, response=response)
        cases.append(evidence)
        print(evidence, flush=True)
    unchanged = before == sql(checksum)
    passed = unchanged and all(case['semanticMatch'] for case in cases)
    report = dict(model='Qwen2.5-0.5B-Instruct Q8_0', status='PASS' if passed else 'QUALITY_GATE_FAILED',
                  cases=cases, submittedToSolver=False, databaseUnchanged=unchanged)
    destination = Path(__file__).resolve().parents[2] / 'docs/e2e/model-report.json'
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if passed else 1

if __name__ == '__main__':
    raise SystemExit(main())

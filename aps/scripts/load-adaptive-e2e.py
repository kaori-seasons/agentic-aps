#!/usr/bin/env python3
"""Bounded authenticated read load while a real solve runs. Local fixture only."""
import json
import math
import statistics
import threading
import time
import argparse
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from e2e_demand_planning import Client, ok

local = threading.local()


def read_progress(_):
    if not hasattr(local, 'client'):
        local.client = Client('http://127.0.0.1:31010/mgr/jvs-aps')
        local.client.login()
    started = time.perf_counter()
    result = ok(local.client.call('/smart-scheduling/plan/progress'))
    return (time.perf_counter()-started)*1000, result['status']


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--seconds', type=int, default=0, help='0: 200-request burst; 1..120: paced read load')
    args = parser.parse_args()
    assert 0 <= args.seconds <= 120
    started = time.perf_counter()
    with ThreadPoolExecutor(8) as pool:
        if args.seconds:
            def worker(index):
                rows = []
                while time.perf_counter() < started + args.seconds:
                    rows.append(read_progress(index))
                    time.sleep(.1)
                return rows
            results = [row for batch in pool.map(worker, range(8)) for row in batch]
        else:
            results = list(pool.map(read_progress, range(200)))
    elapsed = time.perf_counter()-started
    values = sorted(r[0] for r in results)
    report = dict(status='PASS', requests=len(results), concurrency=8,
                  endpoint='/smart-scheduling/plan/progress', errors=0,
                  statuses=sorted(set(r[1] for r in results)), elapsedSeconds=elapsed,
                  meanMs=statistics.mean(values), p95Ms=values[math.ceil(.95*len(values))-1], maxMs=max(values),
                  scope=f'Single local run during one solve; paced duration {args.seconds}s (0=burst); excludes login latency. Not a production SLA.')
    assert any(s not in ('NONE', 'SUCCESS', 'FAILURE', 'NO_SCHEDULED') for s in report['statuses']), report
    filename = 'http-read-soak.json' if args.seconds else 'http-read-load.json'
    path = Path(__file__).resolve().parents[2]/'docs/e2e/adaptive'/filename
    path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(report,ensure_ascii=False,indent=2))

#!/usr/bin/env python3
"""Print SQL for the disposable aps_e2e database; never connects to a database."""
from pathlib import Path

root = Path(__file__).resolve().parents[2]
seed = (root / 'demo-seed.sql').read_text()
seed = seed.replace('USE `jvs-aps`;', 'USE `aps_e2e`;')
seed = seed.replace("SET @tenant := '1';", "SET @tenant := 'demo';")
for day in range(16, 22):
    seed = seed.replace(f'2026-04-{day:02}', f'2026-11-{day:02}')
print(seed)
print("""
START TRANSACTION;
DELETE FROM jvs_plan_task_pending WHERE tenant_id='demo';
DELETE FROM jvs_plan_task_order_pending WHERE tenant_id='demo';
DELETE FROM jvs_plan_task_adjust WHERE tenant_id='demo';
DELETE FROM jvs_plan_task_order WHERE tenant_id='demo';
DELETE FROM jvs_plan_task WHERE tenant_id='demo';
DELETE FROM jvs_plan_info WHERE tenant_id='demo';
UPDATE jvs_production_order SET scheduling_status='UNSCHEDULED' WHERE tenant_id='demo';
UPDATE jvs_material SET quantity=0 WHERE tenant_id='demo' AND id IN ('m-sf-housing','m-sf-rotor');
COMMIT;
""")

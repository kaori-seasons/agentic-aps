UPDATE jvs_plan_task SET start_time=DATE_ADD(start_time,INTERVAL 7 DAY),end_time=DATE_ADD(end_time,INTERVAL 7 DAY) WHERE tenant_id='demo' AND code='SYN-FL-002-TEST';

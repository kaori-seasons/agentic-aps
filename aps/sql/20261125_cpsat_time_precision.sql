-- Apply before enabling CP-SAT candidate publication. Widening preserves existing values.
-- Stop scheduling writes during deployment; take the usual database backup first.
ALTER TABLE jvs_plan_task MODIFY start_time DATETIME(3) NULL DEFAULT NULL COMMENT '计划开始时间', MODIFY end_time DATETIME(3) NULL DEFAULT NULL COMMENT '计划完成时间';
ALTER TABLE jvs_plan_task_pending MODIFY start_time DATETIME(3) NULL DEFAULT NULL COMMENT '计划开始时间', MODIFY end_time DATETIME(3) NULL DEFAULT NULL COMMENT '计划完成时间';
ALTER TABLE jvs_plan_task_adjust MODIFY start_time DATETIME(3) NULL DEFAULT NULL COMMENT '计划开始时间', MODIFY end_time DATETIME(3) NULL DEFAULT NULL COMMENT '计划完成时间';

-- ===============================================
-- APS 测试数据脚本
-- 生成时间: 2026-05-05
-- 用途: 智能排产演示 - 多订单资源争夺场景
-- ===============================================

USE jvs-aps;

-- ===============================================
-- 0. 清理旧数据（避免冲突）
-- ===============================================
SET FOREIGN_KEY_CHECKS=0;
TRUNCATE TABLE jvs_plan_task_order_pending;
TRUNCATE TABLE jvs_plan_task;
TRUNCATE TABLE jvs_production_order;
TRUNCATE TABLE jvs_manufacture_bom;
TRUNCATE TABLE jvs_process_route;
TRUNCATE TABLE jvs_process;
TRUNCATE TABLE jvs_production_resource;
TRUNCATE TABLE jvs_planning_strategy;
DELETE FROM jvs_material;
SET FOREIGN_KEY_CHECKS=1;

-- ===============================================
-- 1. 物料数据 (jvs_material)
-- 说明: 成品库存设置较低，触发MRP缺料计算
-- ===============================================
INSERT INTO jvs_material (id, name, code, type, source, quantity, safety_stock, unit, lead_time, buffer_time, del_flag, create_time, update_time, tenant_id) VALUES
('mat-001', '钢板A', 'MAT-STEEL-A', 'RAW_MATERIAL', 'PURCHASED', 100.000000, 50.000000, '张', '3D', '1H', 0, NOW(), NOW(), 'demo'),
('mat-002', '钢板B', 'MAT-STEEL-B', 'RAW_MATERIAL', 'PURCHASED', 50.000000, 30.000000, '张', '3D', '1H', 0, NOW(), NOW(), 'demo'),
('mat-003', '电机X', 'MAT-MOTOR-X', 'RAW_MATERIAL', 'PURCHASED', 80.000000, 40.000000, '个', '5D', '2H', 0, NOW(), NOW(), 'demo'),
('mat-004', '电机Y', 'MAT-MOTOR-Y', 'RAW_MATERIAL', 'PURCHASED', 60.000000, 30.000000, '个', '5D', '2H', 0, NOW(), NOW(), 'demo'),
('mat-005', '齿轮组', 'MAT-GEAR-SET', 'SEMI_FINISHED', 'PRODUCED', 30.000000, 20.000000, '套', '2D', '1H', 0, NOW(), NOW(), 'demo'),
('mat-006', '半成品A', 'MAT-SEMI-A', 'SEMI_FINISHED', 'PRODUCED', 20.000000, 10.000000, '件', '1D', '0H', 0, NOW(), NOW(), 'demo'),
('mat-007', '控制器', 'MAT-CONTROLLER', 'RAW_MATERIAL', 'PURCHASED', 100.000000, 50.000000, '个', '4D', '1H', 0, NOW(), NOW(), 'demo'),
('mat-008', '成品A', 'MAT-FINISHED-A', 'FINISHED', 'PRODUCED', 10.000000, 5.000000, '台', '1D', '0H', 0, NOW(), NOW(), 'demo'),
('mat-009', '成品B', 'MAT-FINISHED-B', 'FINISHED', 'PRODUCED', 10.000000, 5.000000, '台', '1D', '0H', 0, NOW(), NOW(), 'demo'),
('mat-010', '包装箱', 'MAT-PACKAGE', 'RAW_MATERIAL', 'PURCHASED', 200.000000, 100.000000, '个', '1D', '0H', 0, NOW(), NOW(), 'demo');

-- ===============================================
-- 2. BOM数据 (jvs_manufacture_bom)
-- 关键: 定义成品的子物料构成，触发MRP展开计算
-- BOM结构:
--   成品A(mat-008) -> 半成品A(mat-006) + 齿轮组(mat-005) + 电机X(mat-003) + 控制器(mat-007) + 包装箱(mat-010)
--   半成品A(mat-006) -> 钢板A(mat-001) + 齿轮组(mat-005)
--   齿轮组(mat-005) -> 钢板A(mat-001) + 钢板B(mat-002)
--   成品B(mat-009) -> 半成品A(mat-006) + 电机Y(mat-004) + 包装箱(mat-010)
-- ===============================================
INSERT INTO jvs_manufacture_bom (id, material_id, child_materials, del_flag, create_time, update_time, tenant_id) VALUES
(
  'bom-008',
  'mat-008',
  JSON_ARRAY(
    JSON_OBJECT('materialId','mat-006','quantity',1.000000,'bomType','GENERATE'),
    JSON_OBJECT('materialId','mat-003','quantity',1.000000,'bomType','GENERATE'),
    JSON_OBJECT('materialId','mat-007','quantity',1.000000,'bomType','GENERATE'),
    JSON_OBJECT('materialId','mat-010','quantity',1.000000,'bomType','PURCHASED')
  ),
  0, NOW(), NOW(), 'demo'
),
(
  'bom-009',
  'mat-009',
  JSON_ARRAY(
    JSON_OBJECT('materialId','mat-006','quantity',1.000000,'bomType','GENERATE'),
    JSON_OBJECT('materialId','mat-004','quantity',1.000000,'bomType','GENERATE'),
    JSON_OBJECT('materialId','mat-010','quantity',1.000000,'bomType','PURCHASED')
  ),
  0, NOW(), NOW(), 'demo'
),
(
  'bom-006',
  'mat-006',
  JSON_ARRAY(
    JSON_OBJECT('materialId','mat-001','quantity',2.000000,'bomType','PURCHASED'),
    JSON_OBJECT('materialId','mat-005','quantity',1.000000,'bomType','GENERATE')
  ),
  0, NOW(), NOW(), 'demo'
),
(
  'bom-005',
  'mat-005',
  JSON_ARRAY(
    JSON_OBJECT('materialId','mat-001','quantity',1.000000,'bomType','PURCHASED'),
    JSON_OBJECT('materialId','mat-002','quantity',1.000000,'bomType','PURCHASED')
  ),
  0, NOW(), NOW(), 'demo'
);

-- ===============================================
-- 3. 工艺路线数据 (jvs_process_route)
-- ===============================================
INSERT INTO jvs_process_route (id, material_id, route_design, enabled, del_flag, create_time, update_time, tenant_id) VALUES
('route-001', 'mat-008', '{"nodes":[{"id":"proc-001","data":{"code":"PROC-PRESS","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-001","throughput":"5H"}]}},{"id":"proc-003","data":{"code":"PROC-CNC","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-005","throughput":"8H"}]}},{"id":"proc-004","data":{"code":"PROC-ASSEMBLY","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-007","throughput":"10H"}]}},{"id":"proc-005","data":{"code":"PROC-QC","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-009","throughput":"2H"}]}},{"id":"proc-006","data":{"code":"PROC-PACK","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-010","throughput":"3H"}]}}]}', 1, 0, NOW(), NOW(), 'demo'),
('route-002', 'mat-009', '{"nodes":[{"id":"proc-001","data":{"code":"PROC-PRESS","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-002","throughput":"6H"}]}},{"id":"proc-004","data":{"code":"PROC-ASSEMBLY","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-008","throughput":"12H"}]}},{"id":"proc-005","data":{"code":"PROC-QC","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-009","throughput":"2H"}]}},{"id":"proc-006","data":{"code":"PROC-PACK","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-010","throughput":"3H"}]}}]}', 1, 0, NOW(), NOW(), 'demo'),
('route-003', 'mat-006', '{"nodes":[{"id":"proc-003","data":{"code":"PROC-CNC","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-006","throughput":"4H"}]}},{"id":"proc-005","data":{"code":"PROC-QC","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-009","throughput":"1H"}]}}]}', 1, 0, NOW(), NOW(), 'demo'),
('route-004', 'mat-005', '{"nodes":[{"id":"proc-002","data":{"code":"PROC-WELD","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-003","throughput":"3H"}]}},{"id":"proc-005","data":{"code":"PROC-QC","preIntervalDuration":"0H","postIntervalDuration":"0H","processRelationship":"EE","bufferTime":"0H","useMainResources":[{"id":"res-009","throughput":"1H"}]}}]}', 1, 0, NOW(), NOW(), 'demo');

-- ===============================================
-- 4. 工艺数据 (jvs_process)
-- ===============================================
INSERT INTO jvs_process (id, name, code, pre_interval_duration, post_interval_duration, process_relationship, buffer_time, batch_strategy, use_main_resources, use_auxiliary_resources, use_materials, del_flag, create_time, update_time, tenant_id) VALUES
('proc-001', '冲压', 'PROC-PRESS', '0', '0', 'EE', '0', '{"batchType":"NONE"}', '["res-001","res-002"]', '[]', '[]', 0, NOW(), NOW(), 'demo'),
('proc-002', '焊接', 'PROC-WELD', '0', '0', 'EE', '1', '{"batchType":"NONE"}', '["res-003","res-004"]', '[]', '[]', 0, NOW(), NOW(), 'demo'),
('proc-003', 'CNC加工', 'PROC-CNC', '0', '0', 'EE', '0', '{"batchType":"NONE"}', '["res-005","res-006"]', '[]', '[]', 0, NOW(), NOW(), 'demo'),
('proc-004', '装配', 'PROC-ASSEMBLY', '0', '0', 'EE', '0', '{"batchType":"NONE"}', '["res-007","res-008"]', '[]', '[]', 0, NOW(), NOW(), 'demo'),
('proc-005', '质检', 'PROC-QC', '0', '0', 'EE', '0', '{"batchType":"NONE"}', '["res-009"]', '[]', '[]', 0, NOW(), NOW(), 'demo'),
('proc-006', '包装', 'PROC-PACK', '0', '0', 'EE', '0', '{"batchType":"NONE"}', '["res-010"]', '[]', '[]', 0, NOW(), NOW(), 'demo');

-- ===============================================
-- 5. 生产资源数据 (jvs_production_resource)
-- ===============================================
INSERT INTO jvs_production_resource (id, name, code, resource_group, capacity, unit, throughput, resource_status, del_flag, create_time, update_time, tenant_id) VALUES
('res-001', '冲压机1号', 'RES-PRESS-01', '冲压车间', 100.000000, '小时', '10', 'NORMAL', 0, NOW(), NOW(), 'demo'),
('res-002', '冲压机2号', 'RES-PRESS-02', '冲压车间', 100.000000, '小时', '10', 'NORMAL', 0, NOW(), NOW(), 'demo'),
('res-003', '焊接机1号', 'RES-WELD-01', '焊接车间', 80.000000, '小时', '8', 'NORMAL', 0, NOW(), NOW(), 'demo'),
('res-004', '焊接机2号', 'RES-WELD-02', '焊接车间', 80.000000, '小时', '8', 'NORMAL', 0, NOW(), NOW(), 'demo'),
('res-005', 'CNC加工中心1', 'RES-CNC-01', '机加工车间', 60.000000, '小时', '6', 'NORMAL', 0, NOW(), NOW(), 'demo'),
('res-006', 'CNC加工中心2', 'RES-CNC-02', '机加工车间', 60.000000, '小时', '6', 'NORMAL', 0, NOW(), NOW(), 'demo'),
('res-007', '装配线A', 'RES-ASSEMBLY-A', '装配车间', 50.000000, '小时', '5', 'NORMAL', 0, NOW(), NOW(), 'demo'),
('res-008', '装配线B', 'RES-ASSEMBLY-B', '装配车间', 50.000000, '小时', '5', 'NORMAL', 0, NOW(), NOW(), 'demo'),
('res-009', '质检台1', 'RES-QC-01', '质检车间', 100.000000, '小时', '20', 'NORMAL', 0, NOW(), NOW(), 'demo'),
('res-010', '包装线', 'RES-PACK-01', '包装车间', 80.000000, '小时', '15', 'NORMAL', 0, NOW(), NOW(), 'demo');

-- ===============================================
-- 6. 排产策略数据 (jvs_planning_strategy)
-- ===============================================
INSERT INTO jvs_planning_strategy (
  id, name, begin_time, active, config, order_scheduling_rules, optimize_rules,
  del_flag, create_time, update_time, tenant_id
) VALUES (
  'ps-aps-forward',
  'APS标准正排策略',
  '2026-05-05 08:00:00',
  1,
  JSON_OBJECT(
    'direction','FORWARD',
    'materialConstrained',FALSE,
    'maxNoImprovementTime',180,
    'solverProfile','FFD_TABU',
    'entityTabuSize',7,
    'lateAcceptanceSize',400,
    'acceptedCountLimit',1000,
    'constraintWeightConfig', JSON_OBJECT(
      'priorityWeight',50,
      'finishEarlyWeight',1,
      'minimizeTardinessWeight',1,
      'balanceResourceLoadWeight',1,
      'minimizeGapBetweenDependentTasksWeight',1,
      'minimizeSetupTimeWeight',0
    ),
    'randomSeed',20260505
  ),
  JSON_ARRAY(
    JSON_OBJECT('fieldKey','priority','fieldName','优先级','sortRule','DESC'),
    JSON_OBJECT('fieldKey','deliveryTime','fieldName','需求交付时间','sortRule','ASC')
  ),
  JSON_ARRAY(
    JSON_OBJECT('constraintKey','priorityWeight','constraintName','尽量按优先级安排'),
    JSON_OBJECT('constraintKey','minimizeTardinessWeight','constraintName','最小化逾期'),
    JSON_OBJECT('constraintKey','balanceResourceLoadWeight','constraintName','资源负载均衡')
  ),
  0, NOW(), NOW(), 'demo'
);

-- ===============================================
-- 7. 生产订单数据 (jvs_production_order)
-- 场景: 多订单资源争夺，紧急订单需要优先处理
-- 关键: 设置较高优先级触发任务生成
-- ===============================================
INSERT INTO jvs_production_order (id, code, material_code, quantity, delivery_time, priority, sequence, type, order_status, scheduling_status, color, can_schedule, supplement, del_flag, create_time, update_time, tenant_id) VALUES
('po-new-001', 'PO-NEW-001', 'MAT-FINISHED-A', 50.000000, '2026-05-08 18:00:00', 10, 1.000000, 'MANUFACTURE', 'PENDING', 'UNSCHEDULED', '#FF0000', 1, 0, 0, NOW(), NOW(), 'demo'),
('po-new-002', 'PO-NEW-002', 'MAT-FINISHED-A', 80.000000, '2026-05-12 18:00:00', 5, 2.000000, 'MANUFACTURE', 'PENDING', 'UNSCHEDULED', '#00AA00', 1, 0, 0, NOW(), NOW(), 'demo'),
('po-new-003', 'PO-NEW-003', 'MAT-FINISHED-B', 60.000000, '2026-05-15 18:00:00', 3, 3.000000, 'MANUFACTURE', 'PENDING', 'UNSCHEDULED', '#0088FF', 1, 0, 0, NOW(), NOW(), 'demo');

-- ===============================================
-- 完成提示
-- ===============================================
SELECT '测试数据插入完成！' AS message;
SELECT '========================================' AS '';
SELECT 'BOM数据:' AS '';
SELECT mb.id, m.code as material_code, mb.child_materials FROM jvs_manufacture_bom mb
JOIN jvs_material m ON mb.material_id = m.id;
SELECT '========================================' AS '';
SELECT '订单数据:' AS '';
SELECT id, code, priority, quantity, DATE_FORMAT(delivery_time, '%Y-%m-%d %H:%i') as delivery_time, color FROM jvs_production_order ORDER BY priority;
SELECT '========================================' AS '';
SELECT '物料库存:' AS '';
SELECT code, name, quantity FROM jvs_material WHERE type = 'FINISHED';
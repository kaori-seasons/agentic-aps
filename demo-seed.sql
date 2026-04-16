USE `jvs-aps`;
SET NAMES utf8mb4;

SET @tenant := '1';
SET @now := '2026-04-16 10:00:00';

SET @proc_cut := JSON_OBJECT(
  'name','机壳切割',
  'code','CUT',
  'preIntervalDuration',NULL,
  'postIntervalDuration',NULL,
  'processRelationship','ES',
  'bufferTime',NULL,
  'batchStrategy', JSON_OBJECT('batchThreshold',1),
  'useMainResources', JSON_ARRAY(
    JSON_OBJECT('id','r-cut01','capacity',1,'throughput','10pH'),
    JSON_OBJECT('id','r-cut02','capacity',1,'throughput','12pH')
  ),
  'useAuxiliaryResources', JSON_ARRAY(),
  'useMaterials', JSON_ARRAY(
    JSON_OBJECT('id','m-rm-steel','quantity',2.5,'use',TRUE)
  )
);

SET @proc_weld := JSON_OBJECT(
  'name','机壳焊接',
  'code','WELD',
  'preIntervalDuration',NULL,
  'postIntervalDuration',NULL,
  'processRelationship','ES',
  'bufferTime',NULL,
  'batchStrategy', JSON_OBJECT('batchThreshold',1),
  'useMainResources', JSON_ARRAY(
    JSON_OBJECT('id','r-weld01','capacity',1,'throughput','8pH')
  ),
  'useAuxiliaryResources', JSON_ARRAY(),
  'useMaterials', JSON_ARRAY()
);

SET @proc_coat := JSON_OBJECT(
  'name','表面喷涂',
  'code','COAT',
  'preIntervalDuration',NULL,
  'postIntervalDuration',NULL,
  'processRelationship','ES',
  'bufferTime',NULL,
  'batchStrategy', JSON_OBJECT('batchThreshold',1),
  'useMainResources', JSON_ARRAY(
    JSON_OBJECT('id','r-coat01','capacity',1,'throughput','10pH')
  ),
  'useAuxiliaryResources', JSON_ARRAY(),
  'useMaterials', JSON_ARRAY(
    JSON_OBJECT('id','m-rm-powder','quantity',0.15,'use',TRUE)
  )
);

SET @proc_wind := JSON_OBJECT(
  'name','转子绕线',
  'code','WIND',
  'preIntervalDuration',NULL,
  'postIntervalDuration',NULL,
  'processRelationship','ES',
  'bufferTime',NULL,
  'batchStrategy', JSON_OBJECT('batchThreshold',1),
  'useMainResources', JSON_ARRAY(
    JSON_OBJECT('id','r-wind01','capacity',1,'throughput','5pH')
  ),
  'useAuxiliaryResources', JSON_ARRAY(),
  'useMaterials', JSON_ARRAY(
    JSON_OBJECT('id','m-rm-copper','quantity',1.2,'use',TRUE)
  )
);

SET @proc_assy := JSON_OBJECT(
  'name','总成装配',
  'code','ASSY',
  'preIntervalDuration',NULL,
  'postIntervalDuration',NULL,
  'processRelationship','ES',
  'bufferTime',NULL,
  'batchStrategy', JSON_OBJECT('batchThreshold',1),
  'useMainResources', JSON_ARRAY(
    JSON_OBJECT('id','r-assy01','capacity',1,'throughput','6pH'),
    JSON_OBJECT('id','r-assy02','capacity',1,'throughput','6pH')
  ),
  'useAuxiliaryResources', JSON_ARRAY(),
  'useMaterials', JSON_ARRAY(
    JSON_OBJECT('id','m-sf-housing','quantity',1,'use',TRUE),
    JSON_OBJECT('id','m-sf-rotor','quantity',1,'use',TRUE),
    JSON_OBJECT('id','m-rm-bearing','quantity',2,'use',TRUE)
  )
);

SET @proc_test := JSON_OBJECT(
  'name','性能测试',
  'code','TEST',
  'preIntervalDuration',NULL,
  'postIntervalDuration',NULL,
  'processRelationship','ES',
  'bufferTime',NULL,
  'batchStrategy', JSON_OBJECT('batchThreshold',1),
  'useMainResources', JSON_ARRAY(
    JSON_OBJECT('id','r-test01','capacity',1,'throughput','12pH')
  ),
  'useAuxiliaryResources', JSON_ARRAY(),
  'useMaterials', JSON_ARRAY()
);

SET @route_housing := JSON_OBJECT(
  'nodes', JSON_ARRAY(
    JSON_OBJECT('id','cut','data', @proc_cut),
    JSON_OBJECT('id','weld','data', @proc_weld),
    JSON_OBJECT('id','coat','data', @proc_coat)
  ),
  'edges', JSON_ARRAY(
    JSON_OBJECT('source','cut','target','weld'),
    JSON_OBJECT('source','weld','target','coat')
  )
);

SET @route_rotor := JSON_OBJECT(
  'nodes', JSON_ARRAY(
    JSON_OBJECT('id','wind','data', @proc_wind)
  ),
  'edges', JSON_ARRAY()
);

SET @route_fan := JSON_OBJECT(
  'nodes', JSON_ARRAY(
    JSON_OBJECT('id','assy','data', @proc_assy),
    JSON_OBJECT('id','test','data', @proc_test)
  ),
  'edges', JSON_ARRAY(
    JSON_OBJECT('source','assy','target','test')
  )
);

SET @order_info_001 := JSON_OBJECT(
  'code','PO-260416-001',
  'materialId','m-fg-fan-a',
  'materialCode','FG-FAN-A',
  'quantity',20,
  'deliveryTime','2026-04-18 18:00:00',
  'schedulingStatus','SCHEDULED',
  'supplement',FALSE,
  'parentOrderCode',NULL,
  'color','#8EC5FC'
);

SET @order_info_002 := JSON_OBJECT(
  'code','PO-260416-002',
  'materialId','m-fg-fan-a',
  'materialCode','FG-FAN-A',
  'quantity',16,
  'deliveryTime','2026-04-18 20:00:00',
  'schedulingStatus','SCHEDULED',
  'supplement',FALSE,
  'parentOrderCode',NULL,
  'color','#A8E6CF'
);

SET @order_info_003 := JSON_OBJECT(
  'code','PO-260416-003',
  'materialId','m-fg-fan-b',
  'materialCode','FG-FAN-B',
  'quantity',12,
  'deliveryTime','2026-04-19 12:00:00',
  'schedulingStatus','SCHEDULED',
  'supplement',FALSE,
  'parentOrderCode',NULL,
  'color','#FFD3B6'
);

SET @order_info_004 := JSON_OBJECT(
  'code','PO-260416-004',
  'materialId','m-fg-fan-a',
  'materialCode','FG-FAN-A',
  'quantity',18,
  'deliveryTime','2026-04-19 18:00:00',
  'schedulingStatus','SCHEDULED',
  'supplement',FALSE,
  'parentOrderCode',NULL,
  'color','#D0BFFF'
);

SET @material_info_fan_a := JSON_OBJECT(
  'code','FG-FAN-A',
  'name','变频风扇总成A',
  'source','PRODUCED',
  'unit','台'
);

SET @material_info_fan_b := JSON_OBJECT(
  'code','FG-FAN-B',
  'name','变频风扇总成B',
  'source','PRODUCED',
  'unit','台'
);

DELETE FROM jvs_plan_task WHERE id IN (
  'OD0001-cut','OD0001-weld','OD0001-coat','OD0001-wind','OD0001-assy','OD0001-test',
  'OD0002-cut','OD0002-weld','OD0002-coat','OD0002-wind','OD0002-assy','OD0002-test',
  'OD0003-cut','OD0003-weld','OD0003-coat','OD0003-wind','OD0003-assy','OD0003-test',
  'OD0004-cut','OD0004-weld','OD0004-coat','OD0004-wind','OD0004-assy','OD0004-test'
);
DELETE FROM jvs_plan_task_order WHERE id IN ('pto-001','pto-002','pto-003','pto-004');
DELETE FROM jvs_plan_report_field_setting WHERE id IN ('prfs-resource','prfs-order');
DELETE FROM jvs_plan_info WHERE id = 'plan-001';
DELETE FROM jvs_incoming_material_order WHERE id IN ('imo-001','imo-002','imo-003','imo-004');
DELETE FROM jvs_production_order WHERE id IN ('po-001','po-002','po-003','po-004','po-005','po-006');
DELETE FROM jvs_resource_calendar WHERE id IN (
  'rc-cut01','rc-cut02','rc-weld01','rc-coat01','rc-wind01','rc-assy01','rc-assy02','rc-test01'
);
DELETE FROM jvs_work_calendar WHERE id IN ('cal-day','cal-ext');
DELETE FROM jvs_work_mode WHERE id IN ('wm-day','wm-ext');
DELETE FROM jvs_process_route WHERE id IN ('route-housing','route-rotor','route-fan-a','route-fan-b');
DELETE FROM jvs_process WHERE id IN ('proc-cut','proc-weld','proc-coat','proc-wind','proc-assy','proc-test');
DELETE FROM jvs_manufacture_bom WHERE id IN ('bom-housing','bom-rotor','bom-fan-a','bom-fan-b');
DELETE FROM jvs_planning_strategy WHERE id = 'ps-demo-forward';
DELETE FROM jvs_production_resource WHERE id IN (
  'r-cut01','r-cut02','r-weld01','r-coat01','r-wind01','r-assy01','r-assy02','r-test01'
);
DELETE FROM jvs_material WHERE id IN (
  'm-rm-steel','m-rm-powder','m-rm-copper','m-rm-bearing',
  'm-sf-housing','m-sf-rotor','m-fg-fan-a','m-fg-fan-b'
);

INSERT INTO jvs_material (
  id, name, code, type, source, quantity, safety_stock, unit, lead_time, buffer_time,
  del_flag, create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('m-rm-steel','冷轧钢板','RM-STEEL','RAW_MATERIAL','PURCHASED',5000,500,'kg','2D','1D',0,'codex','codex',@now,'codex',@now,@tenant),
  ('m-rm-powder','环氧粉末涂料','RM-POWDER','RAW_MATERIAL','PURCHASED',800,100,'kg','3D','1D',0,'codex','codex',@now,'codex',@now,@tenant),
  ('m-rm-copper','高纯铜线','RM-COPPER','RAW_MATERIAL','PURCHASED',3000,300,'kg','5D','1D',0,'codex','codex',@now,'codex',@now,@tenant),
  ('m-rm-bearing','静音轴承','RM-BEARING','RAW_MATERIAL','PURCHASED',1500,200,'个','4D','1D',0,'codex','codex',@now,'codex',@now,@tenant),
  ('m-sf-housing','风扇机壳半成品','SF-HOUSING','SEMI_FINISHED','PRODUCED',120,20,'件','1D','0.5D',0,'codex','codex',@now,'codex',@now,@tenant),
  ('m-sf-rotor','风扇转子半成品','SF-ROTOR','SEMI_FINISHED','PRODUCED',90,15,'件','1D','0.5D',0,'codex','codex',@now,'codex',@now,@tenant),
  ('m-fg-fan-a','变频风扇总成A','FG-FAN-A','FINISHED','PRODUCED',18,5,'台','1D','0.5D',0,'codex','codex',@now,'codex',@now,@tenant),
  ('m-fg-fan-b','变频风扇总成B','FG-FAN-B','FINISHED','PRODUCED',6,3,'台','1D','0.5D',0,'codex','codex',@now,'codex',@now,@tenant);

INSERT INTO jvs_manufacture_bom (
  id, material_id, child_materials, del_flag, create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('bom-housing','m-sf-housing',JSON_ARRAY(
    JSON_OBJECT('materialId','m-rm-steel','quantity',2.5),
    JSON_OBJECT('materialId','m-rm-powder','quantity',0.15)
  ),0,'codex','codex',@now,'codex',@now,@tenant),
  ('bom-rotor','m-sf-rotor',JSON_ARRAY(
    JSON_OBJECT('materialId','m-rm-copper','quantity',1.2)
  ),0,'codex','codex',@now,'codex',@now,@tenant),
  ('bom-fan-a','m-fg-fan-a',JSON_ARRAY(
    JSON_OBJECT('materialId','m-sf-housing','quantity',1),
    JSON_OBJECT('materialId','m-sf-rotor','quantity',1),
    JSON_OBJECT('materialId','m-rm-bearing','quantity',2)
  ),0,'codex','codex',@now,'codex',@now,@tenant),
  ('bom-fan-b','m-fg-fan-b',JSON_ARRAY(
    JSON_OBJECT('materialId','m-sf-housing','quantity',1),
    JSON_OBJECT('materialId','m-sf-rotor','quantity',1),
    JSON_OBJECT('materialId','m-rm-bearing','quantity',2)
  ),0,'codex','codex',@now,'codex',@now,@tenant);

INSERT INTO jvs_production_resource (
  id, name, code, resource_group, capacity, unit, throughput, resource_status,
  del_flag, create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('r-cut01','切割1','QG1','切割',1,'台','10pH','NORMAL',0,'codex','codex',@now,'codex',@now,@tenant),
  ('r-cut02','切割2','QG2','切割',1,'台','12pH','NORMAL',0,'codex','codex',@now,'codex',@now,@tenant),
  ('r-weld01','焊接1','HJ1','焊接',1,'台','8pH','NORMAL',0,'codex','codex',@now,'codex',@now,@tenant),
  ('r-coat01','喷涂1','PQ1','喷涂',1,'台','10pH','NORMAL',0,'codex','codex',@now,'codex',@now,@tenant),
  ('r-wind01','绕线1','RL1','绕线',1,'台','5pH','NORMAL',0,'codex','codex',@now,'codex',@now,@tenant),
  ('r-assy01','总装1','ZZ1','总装',1,'台','6pH','NORMAL',0,'codex','codex',@now,'codex',@now,@tenant),
  ('r-assy02','总装2','ZZ2','总装',1,'台','6pH','NORMAL',0,'codex','codex',@now,'codex',@now,@tenant),
  ('r-test01','测试1','CS1','测试',1,'台','12pH','NORMAL',0,'codex','codex',@now,'codex',@now,@tenant);

INSERT INTO jvs_process (
  id, name, code, pre_interval_duration, post_interval_duration, process_relationship, buffer_time,
  batch_strategy, use_main_resources, use_auxiliary_resources, use_materials,
  del_flag, create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('proc-cut','机壳切割','CUT',NULL,NULL,'ES',NULL,JSON_OBJECT('batchThreshold',1),
    JSON_ARRAY(JSON_OBJECT('id','r-cut01','capacity',1,'throughput','10pH'),JSON_OBJECT('id','r-cut02','capacity',1,'throughput','12pH')),
    JSON_ARRAY(),JSON_ARRAY(JSON_OBJECT('id','m-rm-steel','quantity',2.5,'use',TRUE)),
    0,'codex','codex',@now,'codex',@now,@tenant),
  ('proc-weld','机壳焊接','WELD',NULL,NULL,'ES',NULL,JSON_OBJECT('batchThreshold',1),
    JSON_ARRAY(JSON_OBJECT('id','r-weld01','capacity',1,'throughput','8pH')),
    JSON_ARRAY(),JSON_ARRAY(),
    0,'codex','codex',@now,'codex',@now,@tenant),
  ('proc-coat','表面喷涂','COAT',NULL,NULL,'ES',NULL,JSON_OBJECT('batchThreshold',1),
    JSON_ARRAY(JSON_OBJECT('id','r-coat01','capacity',1,'throughput','10pH')),
    JSON_ARRAY(),JSON_ARRAY(JSON_OBJECT('id','m-rm-powder','quantity',0.15,'use',TRUE)),
    0,'codex','codex',@now,'codex',@now,@tenant),
  ('proc-wind','转子绕线','WIND',NULL,NULL,'ES',NULL,JSON_OBJECT('batchThreshold',1),
    JSON_ARRAY(JSON_OBJECT('id','r-wind01','capacity',1,'throughput','5pH')),
    JSON_ARRAY(),JSON_ARRAY(JSON_OBJECT('id','m-rm-copper','quantity',1.2,'use',TRUE)),
    0,'codex','codex',@now,'codex',@now,@tenant),
  ('proc-assy','总成装配','ASSY',NULL,NULL,'ES',NULL,JSON_OBJECT('batchThreshold',1),
    JSON_ARRAY(JSON_OBJECT('id','r-assy01','capacity',1,'throughput','6pH'),JSON_OBJECT('id','r-assy02','capacity',1,'throughput','6pH')),
    JSON_ARRAY(),JSON_ARRAY(
      JSON_OBJECT('id','m-sf-housing','quantity',1,'use',TRUE),
      JSON_OBJECT('id','m-sf-rotor','quantity',1,'use',TRUE),
      JSON_OBJECT('id','m-rm-bearing','quantity',2,'use',TRUE)
    ),
    0,'codex','codex',@now,'codex',@now,@tenant),
  ('proc-test','性能测试','TEST',NULL,NULL,'ES',NULL,JSON_OBJECT('batchThreshold',1),
    JSON_ARRAY(JSON_OBJECT('id','r-test01','capacity',1,'throughput','12pH')),
    JSON_ARRAY(),JSON_ARRAY(),
    0,'codex','codex',@now,'codex',@now,@tenant);

INSERT INTO jvs_process_route (
  id, material_id, route_design, enabled, del_flag, create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('route-housing','m-sf-housing',@route_housing,1,0,'codex','codex',@now,'codex',@now,@tenant),
  ('route-rotor','m-sf-rotor',@route_rotor,1,0,'codex','codex',@now,'codex',@now,@tenant),
  ('route-fan-a','m-fg-fan-a',@route_fan,1,0,'codex','codex',@now,'codex',@now,@tenant),
  ('route-fan-b','m-fg-fan-b',@route_fan,1,0,'codex','codex',@now,'codex',@now,@tenant);

INSERT INTO jvs_work_mode (
  id, name, working_mode, del_flag, create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('wm-day','标准日班','08:00-12:00;13:00-17:30',0,'codex','codex',@now,'codex',@now,@tenant),
  ('wm-ext','前段两班','08:00-12:00;13:00-17:30;18:00-22:00',0,'codex','codex',@now,'codex',@now,@tenant);

INSERT INTO jvs_work_calendar (
  id, name, work_mode_id, begin_time, end_time, enabled, work_days, priority,
  del_flag, create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('cal-day','总装测试日历','wm-day','2026-01-01 00:00:00','2026-12-31 23:59:59',1,b'0111110',1,0,'codex','codex',@now,'codex',@now,@tenant),
  ('cal-ext','前段加工日历','wm-ext','2026-01-01 00:00:00','2026-12-31 23:59:59',1,b'0111110',1,0,'codex','codex',@now,'codex',@now,@tenant);

INSERT INTO jvs_resource_calendar (id, work_calendar_id, production_resource_id, tenant_id) VALUES
  ('rc-cut01','cal-ext','r-cut01',@tenant),
  ('rc-cut02','cal-ext','r-cut02',@tenant),
  ('rc-weld01','cal-ext','r-weld01',@tenant),
  ('rc-coat01','cal-ext','r-coat01',@tenant),
  ('rc-wind01','cal-ext','r-wind01',@tenant),
  ('rc-assy01','cal-day','r-assy01',@tenant),
  ('rc-assy02','cal-day','r-assy02',@tenant),
  ('rc-test01','cal-day','r-test01',@tenant);

INSERT INTO jvs_planning_strategy (
  id, name, begin_time, active, config, order_scheduling_rules, optimize_rules,
  del_flag, create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES (
  'ps-demo-forward',
  '演示工厂正排策略',
  '2026-04-16 08:00:00',
  1,
  JSON_OBJECT(
    'direction','FORWARD',
    'materialConstrained',TRUE,
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
    'randomSeed',20260416
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
  0,'codex','codex',@now,'codex',@now,@tenant
);

INSERT INTO jvs_incoming_material_order (
  id, code, material_code, quantity, delivery_time, order_status, del_flag,
  create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('imo-001','IMO-260416-001','RM-STEEL',1500,'2026-04-17 09:00:00','PROCESSING',0,'codex','codex',@now,'codex',@now,@tenant),
  ('imo-002','IMO-260416-002','RM-POWDER',180,'2026-04-17 14:00:00','PROCESSING',0,'codex','codex',@now,'codex',@now,@tenant),
  ('imo-003','IMO-260416-003','RM-COPPER',900,'2026-04-18 10:00:00','PROCESSING',0,'codex','codex',@now,'codex',@now,@tenant),
  ('imo-004','IMO-260416-004','RM-BEARING',500,'2026-04-18 16:00:00','PROCESSING',0,'codex','codex',@now,'codex',@now,@tenant);

INSERT INTO jvs_production_order (
  id, code, material_code, quantity, delivery_time, priority, sequence, type, order_status,
  scheduling_status, color, can_schedule, supplement, parent_order_code, del_flag,
  create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('po-001','PO-260416-001','FG-FAN-A',20,'2026-04-18 18:00:00',5,1,'MANUFACTURE','PENDING','SCHEDULED','#8EC5FC',1,0,NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('po-002','PO-260416-002','FG-FAN-A',16,'2026-04-18 20:00:00',4,2,'MANUFACTURE','PENDING','SCHEDULED','#A8E6CF',1,0,NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('po-003','PO-260416-003','FG-FAN-B',12,'2026-04-19 12:00:00',3,3,'MANUFACTURE','PENDING','SCHEDULED','#FFD3B6',1,0,NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('po-004','PO-260416-004','FG-FAN-A',18,'2026-04-19 18:00:00',2,4,'MANUFACTURE','PENDING','SCHEDULED','#D0BFFF',1,0,NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('po-005','PO-260416-005','FG-FAN-B',14,'2026-04-20 12:00:00',2,5,'MANUFACTURE','PENDING','UNSCHEDULED','#FFAAA5',1,0,NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('po-006','PO-260416-006','FG-FAN-A',10,'2026-04-20 18:00:00',1,6,'MANUFACTURE','PENDING','UNSCHEDULED','#B5EAD7',1,0,NULL,0,'codex','codex',@now,'codex',@now,@tenant);

INSERT INTO jvs_plan_task_order (
  id, order_id, order_code, delay_time_string, order_info, order_material_info, create_time, tenant_id
) VALUES
  ('pto-001','po-001','PO-260416-001',NULL,@order_info_001,@material_info_fan_a,@now,@tenant),
  ('pto-002','po-002','PO-260416-002',NULL,@order_info_002,@material_info_fan_a,@now,@tenant),
  ('pto-003','po-003','PO-260416-003',NULL,@order_info_003,@material_info_fan_b,@now,@tenant),
  ('pto-004','po-004','PO-260416-004',NULL,@order_info_004,@material_info_fan_a,@now,@tenant);

INSERT INTO jvs_plan_info (
  id, schedule_start_time, earliest_task_start_time, last_task_assignment_time, plan_status,
  create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('plan-001','2026-04-16 08:00:00','2026-04-16 08:00:00','2026-04-17 15:30:00','CONFIRMED',
   'codex','codex',@now,'codex',@now,@tenant);

INSERT INTO jvs_plan_report_field_setting (
  id, report_type, task_bar_fields, tooltip_fields,
  create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('prfs-resource','PLAN_RESOURCE_TASK_GANTT',
    JSON_ARRAY(JSON_OBJECT('fieldKey','code','fieldName','任务编码')),
    JSON_ARRAY(
      JSON_OBJECT('fieldKey','orderCode','fieldName','订单号'),
      JSON_OBJECT('fieldKey','materialName','fieldName','物料名称'),
      JSON_OBJECT('fieldKey','scheduledQuantity','fieldName','计划数量'),
      JSON_OBJECT('fieldKey','startTime','fieldName','计划开始时间'),
      JSON_OBJECT('fieldKey','endTime','fieldName','计划完成时间'),
      JSON_OBJECT('fieldKey','mainResourceName','fieldName','主资源')
    ),
    'codex','codex',@now,'codex',@now,@tenant),
  ('prfs-order','PLAN_ORDER_TASK_GANTT',
    JSON_ARRAY(JSON_OBJECT('fieldKey','processCode','fieldName','工序编码')),
    JSON_ARRAY(
      JSON_OBJECT('fieldKey','processCode','fieldName','工序编码'),
      JSON_OBJECT('fieldKey','materialName','fieldName','物料名称'),
      JSON_OBJECT('fieldKey','scheduledQuantity','fieldName','计划数量'),
      JSON_OBJECT('fieldKey','startTime','fieldName','计划开始时间'),
      JSON_OBJECT('fieldKey','endTime','fieldName','计划完成时间')
    ),
    'codex','codex',@now,'codex',@now,@tenant);

INSERT INTO jvs_plan_task (
  id, code, merge_task_code, origin_task_code, main_order_id, production_order_id, scheduled_quantity,
  process_code, process_info, front_task_codes, next_task_codes, main_resource_id, start_time, end_time,
  start_task, end_task, supplement, pinned, merge_task, color, input_materials, task_status,
  last_completion_time, quantity_completed, create_by, create_by_id, create_time, update_by, update_time, tenant_id
) VALUES
  ('OD0001-cut','OD0001-cut',NULL,NULL,'po-001','po-001',20,'CUT',@proc_cut,JSON_ARRAY(),JSON_ARRAY('OD0001-weld'),'r-cut01','2026-04-16 08:00:00','2026-04-16 10:00:00',1,0,0,0,0,'#8EC5FC',JSON_ARRAY(JSON_OBJECT('id','m-rm-steel','quantity',50)),'COMPLETED','2026-04-16 09:55:00',20,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0001-weld','OD0001-weld',NULL,NULL,'po-001','po-001',20,'WELD',@proc_weld,JSON_ARRAY('OD0001-cut'),JSON_ARRAY('OD0001-coat'),'r-weld01','2026-04-16 10:30:00','2026-04-16 12:30:00',0,0,0,0,0,'#8EC5FC',JSON_ARRAY(),'COMPLETED','2026-04-16 12:20:00',20,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0001-coat','OD0001-coat',NULL,NULL,'po-001','po-001',20,'COAT',@proc_coat,JSON_ARRAY('OD0001-weld'),JSON_ARRAY('OD0001-assy'),'r-coat01','2026-04-16 13:30:00','2026-04-16 15:30:00',0,0,0,0,0,'#8EC5FC',JSON_ARRAY(JSON_OBJECT('id','m-rm-powder','quantity',3)),'PARTIALLY_COMPLETED','2026-04-16 14:30:00',8,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0001-wind','OD0001-wind',NULL,NULL,'po-001','po-001',20,'WIND',@proc_wind,JSON_ARRAY(),JSON_ARRAY('OD0001-assy'),'r-wind01','2026-04-16 08:00:00','2026-04-16 12:00:00',1,0,0,0,0,'#8EC5FC',JSON_ARRAY(JSON_OBJECT('id','m-rm-copper','quantity',24)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0001-assy','OD0001-assy',NULL,NULL,'po-001','po-001',20,'ASSY',@proc_assy,JSON_ARRAY('OD0001-coat','OD0001-wind'),JSON_ARRAY('OD0001-test'),'r-assy01','2026-04-17 08:00:00','2026-04-17 10:00:00',0,0,0,1,0,'#8EC5FC',JSON_ARRAY(JSON_OBJECT('id','m-sf-housing','quantity',20),JSON_OBJECT('id','m-sf-rotor','quantity',20),JSON_OBJECT('id','m-rm-bearing','quantity',40)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0001-test','OD0001-test',NULL,NULL,'po-001','po-001',20,'TEST',@proc_test,JSON_ARRAY('OD0001-assy'),JSON_ARRAY(),'r-test01','2026-04-17 10:30:00','2026-04-17 11:30:00',0,1,0,1,0,'#8EC5FC',JSON_ARRAY(),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),

  ('OD0002-cut','OD0002-cut',NULL,NULL,'po-002','po-002',16,'CUT',@proc_cut,JSON_ARRAY(),JSON_ARRAY('OD0002-weld'),'r-cut02','2026-04-16 10:00:00','2026-04-16 11:30:00',1,0,0,0,0,'#A8E6CF',JSON_ARRAY(JSON_OBJECT('id','m-rm-steel','quantity',40)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0002-weld','OD0002-weld',NULL,NULL,'po-002','po-002',16,'WELD',@proc_weld,JSON_ARRAY('OD0002-cut'),JSON_ARRAY('OD0002-coat'),'r-weld01','2026-04-16 14:30:00','2026-04-16 16:00:00',0,0,0,0,0,'#A8E6CF',JSON_ARRAY(),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0002-coat','OD0002-coat',NULL,NULL,'po-002','po-002',16,'COAT',@proc_coat,JSON_ARRAY('OD0002-weld'),JSON_ARRAY('OD0002-assy'),'r-coat01','2026-04-16 17:00:00','2026-04-16 18:30:00',0,0,0,0,0,'#A8E6CF',JSON_ARRAY(JSON_OBJECT('id','m-rm-powder','quantity',2.4)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0002-wind','OD0002-wind',NULL,NULL,'po-002','po-002',16,'WIND',@proc_wind,JSON_ARRAY(),JSON_ARRAY('OD0002-assy'),'r-wind01','2026-04-16 12:00:00','2026-04-16 15:00:00',1,0,0,0,0,'#A8E6CF',JSON_ARRAY(JSON_OBJECT('id','m-rm-copper','quantity',19.2)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0002-assy','OD0002-assy',NULL,NULL,'po-002','po-002',16,'ASSY',@proc_assy,JSON_ARRAY('OD0002-coat','OD0002-wind'),JSON_ARRAY('OD0002-test'),'r-assy01','2026-04-17 10:00:00','2026-04-17 12:00:00',0,0,0,0,0,'#A8E6CF',JSON_ARRAY(JSON_OBJECT('id','m-sf-housing','quantity',16),JSON_OBJECT('id','m-sf-rotor','quantity',16),JSON_OBJECT('id','m-rm-bearing','quantity',32)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0002-test','OD0002-test',NULL,NULL,'po-002','po-002',16,'TEST',@proc_test,JSON_ARRAY('OD0002-assy'),JSON_ARRAY(),'r-test01','2026-04-17 13:30:00','2026-04-17 14:30:00',0,1,0,0,0,'#A8E6CF',JSON_ARRAY(),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),

  ('OD0003-cut','OD0003-cut',NULL,NULL,'po-003','po-003',12,'CUT',@proc_cut,JSON_ARRAY(),JSON_ARRAY('OD0003-weld'),'r-cut02','2026-04-16 08:00:00','2026-04-16 09:15:00',1,0,0,0,0,'#FFD3B6',JSON_ARRAY(JSON_OBJECT('id','m-rm-steel','quantity',30)),'COMPLETED','2026-04-16 09:10:00',12,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0003-weld','OD0003-weld',NULL,NULL,'po-003','po-003',12,'WELD',@proc_weld,JSON_ARRAY('OD0003-cut'),JSON_ARRAY('OD0003-coat'),'r-weld01','2026-04-16 13:00:00','2026-04-16 14:00:00',0,0,0,0,0,'#FFD3B6',JSON_ARRAY(),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0003-coat','OD0003-coat',NULL,NULL,'po-003','po-003',12,'COAT',@proc_coat,JSON_ARRAY('OD0003-weld'),JSON_ARRAY('OD0003-assy'),'r-coat01','2026-04-16 15:30:00','2026-04-16 16:30:00',0,0,0,0,0,'#FFD3B6',JSON_ARRAY(JSON_OBJECT('id','m-rm-powder','quantity',1.8)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0003-wind','OD0003-wind',NULL,NULL,'po-003','po-003',12,'WIND',@proc_wind,JSON_ARRAY(),JSON_ARRAY('OD0003-assy'),'r-wind01','2026-04-16 15:00:00','2026-04-16 17:00:00',1,0,0,0,0,'#FFD3B6',JSON_ARRAY(JSON_OBJECT('id','m-rm-copper','quantity',14.4)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0003-assy','OD0003-assy',NULL,NULL,'po-003','po-003',12,'ASSY',@proc_assy,JSON_ARRAY('OD0003-coat','OD0003-wind'),JSON_ARRAY('OD0003-test'),'r-assy02','2026-04-17 08:00:00','2026-04-17 09:30:00',0,0,0,0,0,'#FFD3B6',JSON_ARRAY(JSON_OBJECT('id','m-sf-housing','quantity',12),JSON_OBJECT('id','m-sf-rotor','quantity',12),JSON_OBJECT('id','m-rm-bearing','quantity',24)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0003-test','OD0003-test',NULL,NULL,'po-003','po-003',12,'TEST',@proc_test,JSON_ARRAY('OD0003-assy'),JSON_ARRAY(),'r-test01','2026-04-17 11:30:00','2026-04-17 12:15:00',0,1,0,0,0,'#FFD3B6',JSON_ARRAY(),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),

  ('OD0004-cut','OD0004-cut',NULL,NULL,'po-004','po-004',18,'CUT',@proc_cut,JSON_ARRAY(),JSON_ARRAY('OD0004-weld'),'r-cut01','2026-04-16 13:00:00','2026-04-16 14:30:00',1,0,0,0,0,'#D0BFFF',JSON_ARRAY(JSON_OBJECT('id','m-rm-steel','quantity',45)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0004-weld','OD0004-weld',NULL,NULL,'po-004','po-004',18,'WELD',@proc_weld,JSON_ARRAY('OD0004-cut'),JSON_ARRAY('OD0004-coat'),'r-weld01','2026-04-16 16:30:00','2026-04-16 18:00:00',0,0,0,0,0,'#D0BFFF',JSON_ARRAY(),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0004-coat','OD0004-coat',NULL,NULL,'po-004','po-004',18,'COAT',@proc_coat,JSON_ARRAY('OD0004-weld'),JSON_ARRAY('OD0004-assy'),'r-coat01','2026-04-16 19:00:00','2026-04-16 20:30:00',0,0,0,0,0,'#D0BFFF',JSON_ARRAY(JSON_OBJECT('id','m-rm-powder','quantity',2.7)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0004-wind','OD0004-wind',NULL,NULL,'po-004','po-004',18,'WIND',@proc_wind,JSON_ARRAY(),JSON_ARRAY('OD0004-assy'),'r-wind01','2026-04-16 17:30:00','2026-04-16 20:30:00',1,0,0,0,0,'#D0BFFF',JSON_ARRAY(JSON_OBJECT('id','m-rm-copper','quantity',21.6)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0004-assy','OD0004-assy',NULL,NULL,'po-004','po-004',18,'ASSY',@proc_assy,JSON_ARRAY('OD0004-coat','OD0004-wind'),JSON_ARRAY('OD0004-test'),'r-assy02','2026-04-17 09:30:00','2026-04-17 11:30:00',0,0,0,0,0,'#D0BFFF',JSON_ARRAY(JSON_OBJECT('id','m-sf-housing','quantity',18),JSON_OBJECT('id','m-sf-rotor','quantity',18),JSON_OBJECT('id','m-rm-bearing','quantity',36)),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant),
  ('OD0004-test','OD0004-test',NULL,NULL,'po-004','po-004',18,'TEST',@proc_test,JSON_ARRAY('OD0004-assy'),JSON_ARRAY(),'r-test01','2026-04-17 14:30:00','2026-04-17 15:30:00',0,1,0,0,0,'#D0BFFF',JSON_ARRAY(),'PENDING',NULL,0,'codex','codex',@now,'codex',@now,@tenant);

-- 在启用缺料预警前执行（MySQL 8）。欠料清单、预警台账、规则覆盖与推送记录四张表。
-- 相对《缺料预警与推送接入方案》附录 A 草案的修订，均因草案与代码事实或自身口径冲突：
--   1. shortage_key 去掉 runId，改为业务自然键。草案键含 runId，却要求“旧轮次按同键覆盖”，
--      两者不可能同时成立；含 runId 的键每轮求解都会新增一行，台账无限膨胀。
--   2. 键长放宽。草案 runId(36)+orderCode(255)+materialCode(30) 已超过 VARCHAR(160)；
--      alertKey 由 ruleCode+dimension+entityKey+bucket 拼接，同样超过 VARCHAR(200)。
--   3. 时间列统一 DATETIME(3) 且全部由应用写入。业务侧 LocalDateTime 与库端 CURRENT_TIMESTAMP
--      在会话时区不一致时会相差数小时，去重时间桶与冷却期不能依赖库端时钟。
--   4. 推送记录增加 lease_until。草案只有 lease_token，没有过期时间，投递进程崩溃后行会永久卡在发送中。
--   5. 推送唯一键改为 idempotency_key。草案 (alert_id, channel) 在同一渠道配多个接收端时会撞键。
--   6. 欠料清单的 severity 允许为空。未命中任何规则的缺口（如交期在 14 天外）没有等级，
--      强行记 P4 会与“数据质量提示”的语义混淆。

CREATE TABLE IF NOT EXISTS jvs_shortage_item (
  tenant_id VARCHAR(50) NOT NULL,
  shortage_key VARCHAR(400) NOT NULL COMMENT '业务自然键，不含运行ID：ORDER:{订单编码}:{物料编码} / WEEK:{地点}:{物料}:{规则码}:{引用}[:{周一}]',
  dimension VARCHAR(10) NOT NULL COMMENT 'ORDER 短周期齐套 / WEEK 长周期水位',
  source VARCHAR(10) NOT NULL COMMENT 'MRP / DEMAND',
  run_id VARCHAR(36) NOT NULL COMMENT '最近一次观测到该缺口的求解审计ID或补货运行ID',
  detected_at DATETIME(3) NOT NULL COMMENT '最近一次观测时刻',
  first_detected_at DATETIME(3) NOT NULL COMMENT '本轮连续观测的起点，解决后重新观测则重置',
  data_cutoff DATETIME(3) NULL COMMENT '所依据的数据截止时间',
  severity VARCHAR(4) NULL COMMENT '当前评估的最高等级，未命中规则时为空',
  reason_code VARCHAR(40) NOT NULL COMMENT '当前最高等级命中的规则码，未命中时为观测类别',
  material_id VARCHAR(96) NULL COMMENT '求解侧物料内部ID，需求侧可空',
  material_code VARCHAR(96) NOT NULL,
  material_name VARCHAR(255) NULL,
  unit VARCHAR(96) NULL,
  gap_quantity DECIMAL(20,6) NOT NULL COMMENT '缺口数量，非负',
  required_quantity DECIMAL(20,6) NULL COMMENT '毛需求（求解侧）',
  required_at DATETIME(3) NULL COMMENT '需求时刻，即订单交期',
  week_start DATE NULL COMMENT '发生周的周一',
  order_code VARCHAR(255) NULL,
  location_code VARCHAR(96) NULL,
  reference VARCHAR(160) NULL COMMENT '需求侧 Finding.reference：在途行、供应商或采购单',
  source_reason VARCHAR(64) NULL COMMENT '需求侧 Finding.reason 原值',
  on_hand_available DECIMAL(20,6) NULL COMMENT '轮到本订单时的在库量，缺口归属依据',
  on_hand_consumed DECIMAL(20,6) NOT NULL DEFAULT 0,
  in_transit_consumed DECIMAL(20,6) NOT NULL DEFAULT 0,
  in_transit_skipped DECIMAL(20,6) NOT NULL DEFAULT 0 COMMENT '因到货晚于交期被跳过的在途量',
  skipped_lines MEDIUMTEXT NULL COMMENT 'JSON：被跳过的在途行明细',
  available_in_transit MEDIUMTEXT NULL COMMENT 'JSON：参与分配的在途行明细',
  supply_mode VARCHAR(24) NULL COMMENT 'IN_TRANSIT / IN_TRANSIT_LATE / PURCHASE_PROPOSAL / NONE；NONE 即无到货计划',
  expected_eta DATETIME(3) NULL COMMENT '预计到货时刻，无到货计划时为空且 supply_mode=NONE',
  supplier_code VARCHAR(96) NULL,
  earliest_start DATETIME(3) NULL COMMENT '受影响任务最早可开工',
  delay_duration VARCHAR(20) NULL COMMENT 'ISO-8601 时长',
  downstream_tasks MEDIUMTEXT NULL COMMENT 'JSON：受影响任务',
  safety_stock DECIMAL(20,6) NULL,
  scenario_balance DECIMAL(20,6) NULL,
  detail MEDIUMTEXT NULL COMMENT 'JSON：归属依据等补充信息',
  state VARCHAR(16) NOT NULL COMMENT 'OPEN / ACKNOWLEDGED / RESOLVED / MUTED',
  observed_count INT NOT NULL DEFAULT 1 COMMENT '被不同运行观测到的次数，同一运行重复落库不累加',
  last_evaluated_at DATETIME(3) NULL,
  resolved_by VARCHAR(96) NULL,
  resolved_at DATETIME(3) NULL,
  resolution_note VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id, shortage_key),
  KEY shortage_scope (tenant_id, dimension, state, severity, required_at),
  KEY shortage_run (tenant_id, run_id),
  KEY shortage_material (tenant_id, material_code, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS jvs_shortage_alert (
  tenant_id VARCHAR(50) NOT NULL,
  alert_id CHAR(36) NOT NULL COMMENT '系统签发的UUID，回执与外部幂等都以它为准',
  alert_key VARCHAR(500) NOT NULL COMMENT '{ruleCode}:{dimension}:{entityKey}:{bucket}',
  shortage_key VARCHAR(400) NULL COMMENT '关联欠料清单；数据质量等系统级预警没有欠料条目，为空',
  rule_code VARCHAR(40) NOT NULL,
  dimension VARCHAR(10) NOT NULL,
  entity_key VARCHAR(400) NOT NULL,
  bucket VARCHAR(16) NOT NULL COMMENT '时间桶：P1/P2 小时，P3/P4 天',
  severity VARCHAR(4) NOT NULL,
  title VARCHAR(255) NOT NULL COMMENT '服务端模板生成，各渠道一致',
  payload MEDIUMTEXT NOT NULL COMMENT 'JSON：推送信封中的 payload 对象',
  state VARCHAR(16) NOT NULL COMMENT 'OPEN / ACKNOWLEDGED / RESOLVED / MUTED / SUPERSEDED',
  observed_count INT NOT NULL DEFAULT 1 COMMENT '静默期内的重复观测次数',
  first_observed_at DATETIME(3) NOT NULL,
  last_observed_at DATETIME(3) NOT NULL,
  last_pushed_at DATETIME(3) NULL,
  pushed_count INT NOT NULL DEFAULT 0,
  acknowledged_by VARCHAR(96) NULL,
  acknowledged_at DATETIME(3) NULL,
  resolution_note VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NULL,
  PRIMARY KEY (tenant_id, alert_id),
  UNIQUE KEY alert_once (tenant_id, alert_key, severity),
  KEY alert_entity (tenant_id, rule_code, entity_key, state),
  KEY alert_shortage (tenant_id, shortage_key),
  KEY alert_queue (tenant_id, state, severity, created_at),
  KEY alert_lookup (alert_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 只存租户覆盖值，空列表示沿用代码内置默认值，避免版本升级后租户未改过的字段被旧值锁死。
CREATE TABLE IF NOT EXISTS jvs_shortage_rule (
  tenant_id VARCHAR(50) NOT NULL,
  rule_code VARCHAR(40) NOT NULL,
  dimension VARCHAR(10) NULL,
  severity VARCHAR(4) NULL,
  enabled TINYINT(1) NULL,
  threshold_value INT NULL,
  threshold_unit VARCHAR(16) NULL,
  cooldown_minutes INT NULL,
  receivers VARCHAR(500) NULL COMMENT '接收角色编码，逗号分隔',
  channels VARCHAR(200) NULL COMMENT 'LEDGER / WEBHOOK / OUTBOX / EMAIL，逗号分隔',
  action_hint VARCHAR(200) NULL,
  updated_by VARCHAR(96) NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id, rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- 推送记录兼回执审计。外部渠道以 alert_id 去重，同一 alert_id 重复投递必须幂等。
CREATE TABLE IF NOT EXISTS jvs_shortage_notify (
  tenant_id VARCHAR(50) NOT NULL,
  id CHAR(36) NOT NULL,
  alert_id CHAR(36) NOT NULL,
  channel VARCHAR(32) NOT NULL COMMENT 'LEDGER / WEBHOOK / OUTBOX / EMAIL',
  target VARCHAR(2000) NOT NULL COMMENT '接收端：Webhook 地址或收件人列表',
  idempotency_key VARCHAR(120) NOT NULL COMMENT 'alertId:channel:sha256(target)前32位，推送唯一键',
  state VARCHAR(16) NOT NULL COMMENT 'PENDING / SENDING / SENT / DEAD / SKIPPED',
  attempts INT NOT NULL DEFAULT 0,
  lease_token CHAR(36) NULL,
  lease_until DATETIME(3) NULL COMMENT '租约过期时刻，过期后可被重新认领',
  available_at DATETIME(3) NOT NULL COMMENT '下次可投递时刻，退避重试靠它',
  last_attempt_at DATETIME(3) NULL,
  sent_at DATETIME(3) NULL,
  http_status INT NULL,
  last_error VARCHAR(500) NULL,
  receipt_state VARCHAR(16) NULL COMMENT 'ACK / RESOLVED / MUTED / REJECTED',
  receipt_note VARCHAR(500) NULL,
  receipt_by VARCHAR(96) NULL,
  receipt_at DATETIME(3) NULL,
  payload MEDIUMTEXT NULL COMMENT '投递的信封原文，出站拉取与审计用',
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id, id),
  UNIQUE KEY notify_once (tenant_id, idempotency_key),
  KEY notify_alert (tenant_id, alert_id),
  KEY notify_queue (tenant_id, state, available_at, id),
  KEY notify_rate (tenant_id, channel, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

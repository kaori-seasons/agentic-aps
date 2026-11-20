-- Apply once per APS database before enabling the planning workbench (MySQL 8).
CREATE TABLE IF NOT EXISTS jvs_demand_series (
 tenant_id VARCHAR(50) NOT NULL, location_id VARCHAR(96) NOT NULL, material_id VARCHAR(96) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0, generation BIGINT NOT NULL DEFAULT 0, current_run CHAR(36),
 PRIMARY KEY (tenant_id,location_id,material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS jvs_demand_snapshot (
 tenant_id VARCHAR(50) NOT NULL, location_id VARCHAR(96) NOT NULL, material_id VARCHAR(96) NOT NULL,
 revision BIGINT NOT NULL, payload MEDIUMTEXT NOT NULL, input_digest CHAR(64) NOT NULL, imported_by VARCHAR(96) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY (tenant_id,location_id,material_id,revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS jvs_demand_run (
 tenant_id VARCHAR(50) NOT NULL, id CHAR(36) NOT NULL, request_key VARCHAR(96) NOT NULL,
 location_id VARCHAR(96) NOT NULL, material_id VARCHAR(96) NOT NULL, revision BIGINT NOT NULL,
 generation BIGINT NOT NULL, algorithm VARCHAR(32) NOT NULL, result MEDIUMTEXT NOT NULL,
 overrides_used MEDIUMTEXT NOT NULL, created_by VARCHAR(96) NOT NULL, created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id), UNIQUE KEY run_request(tenant_id,location_id,material_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS jvs_demand_override (
 tenant_id VARCHAR(50) NOT NULL, location_id VARCHAR(96) NOT NULL, material_id VARCHAR(96) NOT NULL,
 week_start DATE NOT NULL, generation BIGINT NOT NULL, quantity DECIMAL(20,6), reason VARCHAR(500) NOT NULL,
 expires_at VARCHAR(40) NOT NULL, approved_by VARCHAR(96) NOT NULL,
 PRIMARY KEY(tenant_id,location_id,material_id,week_start,generation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- The transactional pull-outbox is also the approval audit. External consumers use id as the ERP idempotency key.
CREATE TABLE IF NOT EXISTS jvs_demand_purchase (
 tenant_id VARCHAR(50) NOT NULL, id CHAR(36) NOT NULL, run_id CHAR(36) NOT NULL, proposal_index INT NOT NULL,
 location_id VARCHAR(96) NOT NULL, material_id VARCHAR(96) NOT NULL, supplier VARCHAR(96) NOT NULL,
 unit VARCHAR(96) NOT NULL, quantity DECIMAL(20,6) NOT NULL, release_at VARCHAR(40) NOT NULL, available_at VARCHAR(40) NOT NULL,
 reason VARCHAR(500) NOT NULL, approved_by VARCHAR(96) NOT NULL, state VARCHAR(20) NOT NULL,
 external_line VARCHAR(96), receipt_note VARCHAR(500), created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id), UNIQUE KEY purchase_once(tenant_id,run_id,proposal_index),
 UNIQUE KEY erp_line_once(tenant_id,external_line), KEY purchase_scope(tenant_id,location_id,material_id,state),
 KEY dispatch_queue(tenant_id,state,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS jvs_demand_job (
 tenant_id VARCHAR(50) NOT NULL, id CHAR(36) NOT NULL, location_id VARCHAR(96) NOT NULL,
 material_id VARCHAR(96) NOT NULL, request_key VARCHAR(96) NOT NULL, revision BIGINT NOT NULL,
 generation BIGINT NOT NULL, created_by VARCHAR(96) NOT NULL, state VARCHAR(20) NOT NULL,
 attempts INT NOT NULL DEFAULT 0, lease_token CHAR(36), available_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 run_id CHAR(36), detail VARCHAR(240), created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id), UNIQUE KEY job_request(tenant_id,location_id,material_id,request_key),
 KEY job_queue(state,available_at,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS jvs_demand_adjustment (
 tenant_id VARCHAR(50) NOT NULL, id CHAR(36) NOT NULL, location_id VARCHAR(96) NOT NULL, material_id VARCHAR(96) NOT NULL,
 generation BIGINT NOT NULL, week_start DATE NOT NULL, quantity DECIMAL(20,6), expires_at VARCHAR(40) NOT NULL,
 reason VARCHAR(500) NOT NULL, submitted_by VARCHAR(96) NOT NULL, approved_by VARCHAR(96), state VARCHAR(16) NOT NULL,
 created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id), KEY review_queue(tenant_id,location_id,material_id,state,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

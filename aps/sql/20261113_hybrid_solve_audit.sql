-- 在启用本次后端版本前执行。每次求解保存独立快照，确认或取消计划均不删除审计记录。
CREATE TABLE IF NOT EXISTS jvs_solve_audit (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    input_json LONGTEXT NOT NULL,
    result_json LONGTEXT,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_solve_audit_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

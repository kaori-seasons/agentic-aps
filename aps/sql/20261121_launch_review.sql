-- Apply after 20261120_demand_planning.sql, before deploying the launch-review endpoints.
CREATE TABLE IF NOT EXISTS jvs_demand_launch_review (
 tenant_id VARCHAR(50) NOT NULL, id CHAR(36) NOT NULL,
 location_id VARCHAR(96) NOT NULL, material_id VARCHAR(96) NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 PRIMARY KEY(tenant_id,id),
 KEY series_reviews(tenant_id,location_id,material_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

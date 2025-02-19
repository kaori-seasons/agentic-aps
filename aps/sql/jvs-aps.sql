/*
 Navicat Premium Dump SQL

 Source Server         : 体验环境
 Source Server Type    : MySQL
 Source Server Version : 80032 (8.0.32)
 Source Host           : 101.227.48.55:3306
 Source Schema         : jvs-aps

 Target Server Type    : MySQL
 Target Server Version : 80032 (8.0.32)
 File Encoding         : 65001

 Date: 19/02/2025 17:07:08
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for jvs_incoming_material_order
-- ----------------------------
DROP TABLE IF EXISTS `jvs_incoming_material_order`;
CREATE TABLE `jvs_incoming_material_order`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单编码',
  `material_code` char(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码',
  `quantity` decimal(18, 6) NULL DEFAULT NULL COMMENT '数量',
  `delivery_time` datetime NULL DEFAULT NULL COMMENT '预计到货时间',
  `order_status` enum('PROCESSING','CLOSED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '状态',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0未删除  1已删除',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '来料订单' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_incoming_material_order
-- ----------------------------

-- ----------------------------
-- Table structure for jvs_manufacture_bom
-- ----------------------------
DROP TABLE IF EXISTS `jvs_manufacture_bom`;
CREATE TABLE `jvs_manufacture_bom`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `material_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料id',
  `child_materials` json NULL COMMENT '子件物料',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0未删除  1已删除',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '制造BOM' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_manufacture_bom
-- ----------------------------
INSERT INTO `jvs_manufacture_bom` VALUES ('0d855308e4d8fd3aa6bfe7cf9aceff7a', '893d901a0d3ca3a75e201be530467036', '[{\"quantity\": 1, \"materialId\": \"8ef50c2463a8cfb07d6a99dc7aec49c2\"}, {\"quantity\": 1, \"materialId\": \"7ceb314ae11af93c7bfaca2200b10148\"}]', 0, 'admin2', '1', '2025-02-18 11:20:24', 'admin2', '2025-02-18 11:20:24', '1');
INSERT INTO `jvs_manufacture_bom` VALUES ('31c289b9b108bfdf9d086b6c540fbe5b', '327d114c3a7f5a9d01cca2dbaeb5b9dd', '[{\"quantity\": 1, \"materialId\": \"dcb347ab06cc3a88981ec5aea0fbec8e\"}, {\"quantity\": 1, \"materialId\": \"8c19bf73f842d3eeee3f02e4666e077b\"}, {\"quantity\": 1, \"materialId\": \"893d901a0d3ca3a75e201be530467036\"}, {\"quantity\": 1, \"materialId\": \"7b576af9247c2d2278b135b8e8c546ba\"}, {\"quantity\": 1, \"materialId\": \"8ef50c2463a8cfb07d6a99dc7aec49c2\"}, {\"quantity\": 1, \"materialId\": \"7ceb314ae11af93c7bfaca2200b10148\"}, {\"quantity\": 1, \"materialId\": \"861fe861d34ae7b6cd840275df7deb25\"}, {\"quantity\": 1, \"materialId\": \"18c92cc507fd2824be22ef4a9db11116\"}, {\"quantity\": 1, \"materialId\": \"e320bcad3ce32360902350c53bb42049\"}, {\"quantity\": 1, \"materialId\": \"291191f4c070b2bdb5f185b63ea366ce\"}, {\"quantity\": 1, \"materialId\": \"8fee5a4040ba738989ca9bbc8df91ad8\"}]', 0, 'admin2', '1', '2025-02-18 11:18:13', 'admin2', '2025-02-18 11:18:13', '1');
INSERT INTO `jvs_manufacture_bom` VALUES ('5323b7c42d033d8b2b3a628f4ec59a7b', '8c19bf73f842d3eeee3f02e4666e077b', '[{\"quantity\": 1, \"materialId\": \"291191f4c070b2bdb5f185b63ea366ce\"}, {\"quantity\": 1, \"materialId\": \"7b576af9247c2d2278b135b8e8c546ba\"}, {\"quantity\": 1, \"materialId\": \"893d901a0d3ca3a75e201be530467036\"}]', 0, 'admin2', '1', '2025-02-18 11:20:10', 'admin2', '2025-02-18 11:20:10', '1');
INSERT INTO `jvs_manufacture_bom` VALUES ('9527d8a333edfe19e82ca27853dc8326', '18c92cc507fd2824be22ef4a9db11116', '[{\"quantity\": 1, \"materialId\": \"7ceb314ae11af93c7bfaca2200b10148\"}, {\"quantity\": 1, \"materialId\": \"8ef50c2463a8cfb07d6a99dc7aec49c2\"}, {\"quantity\": 1, \"materialId\": \"7b576af9247c2d2278b135b8e8c546ba\"}]', 0, 'admin2', '1', '2025-02-18 11:20:51', 'admin2', '2025-02-18 11:20:51', '1');
INSERT INTO `jvs_manufacture_bom` VALUES ('e36ba3ec93e09154428d5dbe33448bf5', '7ceb314ae11af93c7bfaca2200b10148', '[{\"quantity\": 1, \"materialId\": \"e320bcad3ce32360902350c53bb42049\"}]', 0, 'admin2', '1', '2025-02-18 11:20:37', 'admin2', '2025-02-18 11:20:37', '1');

-- ----------------------------
-- Table structure for jvs_material
-- ----------------------------
DROP TABLE IF EXISTS `jvs_material`;
CREATE TABLE `jvs_material`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名称',
  `code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '编码',
  `type` enum('RAW_MATERIAL','SEMI_FINISHED','FINISHED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型',
  `source` enum('PRODUCED','PURCHASED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '来源',
  `quantity` decimal(18, 6) NULL DEFAULT NULL COMMENT '库存',
  `safety_stock` decimal(18, 6) NULL DEFAULT NULL COMMENT '安全库存',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '计量单位',
  `lead_time` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '提前期',
  `buffer_time` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '缓冲期',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0未删除  1已删除',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code_tenant_id`(`code` ASC, `tenant_id` ASC) USING BTREE COMMENT '物料编码租户级唯一',
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '物料' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_material
-- ----------------------------
INSERT INTO `jvs_material` VALUES ('18c92cc507fd2824be22ef4a9db11116', '汽车发动机-油管', 'L0008', 'RAW_MATERIAL', 'PRODUCED', 500.000000, 300.000000, '根', NULL, NULL, 0, 'admin2', '1', '2025-02-18 10:07:52', 'admin2', '2025-02-18 14:22:34', '1');
INSERT INTO `jvs_material` VALUES ('291191f4c070b2bdb5f185b63ea366ce', '汽车发动机-密封件', 'L0010', 'RAW_MATERIAL', 'PRODUCED', 500.000000, 300.000000, '件', NULL, NULL, 0, 'admin2', '1', '2025-02-18 10:09:01', 'admin2', '2025-02-18 14:22:23', '1');
INSERT INTO `jvs_material` VALUES ('327d114c3a7f5a9d01cca2dbaeb5b9dd', '汽车发动机', 'L0012', 'FINISHED', 'PRODUCED', 1.000000, 0.000000, '台', NULL, NULL, 0, 'admin2', '1', '2025-02-18 11:16:35', 'admin2', '2025-02-18 14:22:08', '1');
INSERT INTO `jvs_material` VALUES ('7b576af9247c2d2278b135b8e8c546ba', '汽车发动机-连杆', 'L0004', 'RAW_MATERIAL', 'PRODUCED', 500.000000, 300.000000, '根', NULL, NULL, 0, 'admin2', '1', '2025-02-18 10:04:40', 'admin2', '2025-02-18 14:23:19', '1');
INSERT INTO `jvs_material` VALUES ('7ceb314ae11af93c7bfaca2200b10148', '汽车发动机-气门', 'L0006', 'RAW_MATERIAL', 'PRODUCED', 500.000000, 300.000000, '个', NULL, NULL, 0, 'admin2', '1', '2025-02-18 10:06:08', 'admin2', '2025-02-18 14:23:12', '1');
INSERT INTO `jvs_material` VALUES ('861fe861d34ae7b6cd840275df7deb25', '汽车发动机-进排气歧管', 'L0007', 'RAW_MATERIAL', 'PRODUCED', 500.000000, 300.000000, '根', NULL, NULL, 0, 'admin2', '1', '2025-02-18 10:07:18', 'admin2', '2025-02-18 14:22:40', '1');
INSERT INTO `jvs_material` VALUES ('893d901a0d3ca3a75e201be530467036', '汽车发动机-曲轴', 'L0003', 'RAW_MATERIAL', 'PRODUCED', 500.000000, 300.000000, '件', NULL, NULL, 0, 'admin2', '1', '2025-02-18 10:03:54', 'admin2', '2025-02-18 14:24:09', '1');
INSERT INTO `jvs_material` VALUES ('8c19bf73f842d3eeee3f02e4666e077b', '汽车发动机-缸盖', 'L0002', 'RAW_MATERIAL', 'PRODUCED', 500.000000, 300.000000, '件', NULL, NULL, 0, 'admin2', '1', '2025-02-18 10:03:14', 'admin2', '2025-02-18 14:24:25', '1');
INSERT INTO `jvs_material` VALUES ('8ef50c2463a8cfb07d6a99dc7aec49c2', '汽车发动机-活塞', 'L0005', 'RAW_MATERIAL', 'PRODUCED', 500.000000, 300.000000, '个', NULL, NULL, 0, 'admin2', '1', '2025-02-18 10:05:28', 'admin2', '2025-02-18 14:22:48', '1');
INSERT INTO `jvs_material` VALUES ('8fee5a4040ba738989ca9bbc8df91ad8', '汽车发动机-轴承', 'L0011', 'RAW_MATERIAL', 'PRODUCED', 500.000000, 300.000000, '个', NULL, NULL, 0, 'admin2', '1', '2025-02-18 10:09:31', 'admin2', '2025-02-18 14:22:17', '1');
INSERT INTO `jvs_material` VALUES ('dcb347ab06cc3a88981ec5aea0fbec8e', '汽车发动机-缸体', 'L0001', 'RAW_MATERIAL', 'PRODUCED', 500.000000, 500.000000, '件', NULL, NULL, 0, 'admin2', '1', '2025-02-18 10:02:37', 'admin2', '2025-02-18 14:25:30', '1');
INSERT INTO `jvs_material` VALUES ('e320bcad3ce32360902350c53bb42049', '汽车发动机-水泵', 'L0009', 'RAW_MATERIAL', 'PRODUCED', 500.000000, 300.000000, '个', NULL, NULL, 0, 'admin2', '1', '2025-02-18 10:08:20', 'admin2', '2025-02-18 14:22:29', '1');

-- ----------------------------
-- Table structure for jvs_plan_info
-- ----------------------------
DROP TABLE IF EXISTS `jvs_plan_info`;
CREATE TABLE `jvs_plan_info`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `schedule_start_time` datetime NULL DEFAULT NULL COMMENT '计划开始时间',
  `last_confirm_schedule_time` datetime NULL DEFAULT NULL COMMENT '最近确认计划的时间',
  `earliest_task_start_time` datetime NULL DEFAULT NULL COMMENT '任务最早开始时间',
  `last_task_assignment_time` datetime NULL DEFAULT NULL COMMENT '最近任务派工截止时间',
  `plan_status` enum('CONFIRMED','UNCONFIRMED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '计划状态',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '排产计划基本信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_plan_info
-- ----------------------------

-- ----------------------------
-- Table structure for jvs_plan_report_field_setting
-- ----------------------------
DROP TABLE IF EXISTS `jvs_plan_report_field_setting`;
CREATE TABLE `jvs_plan_report_field_setting`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `report_type` enum('PLAN_RESOURCE_TASK_GANTT','PLAN_ORDER_TASK_GANTT') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '报告类型',
  `task_bar_fields` json NULL COMMENT '任务条显示字段',
  `tooltip_fields` json NULL COMMENT '任务条提示框显示字段',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务可视化字段设置' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_plan_report_field_setting
-- ----------------------------

-- ----------------------------
-- Table structure for jvs_plan_task
-- ----------------------------
DROP TABLE IF EXISTS `jvs_plan_task`;
CREATE TABLE `jvs_plan_task`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务编码',
  `merge_task_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '合并任务的编码',
  `origin_task_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '源任务编码（拆分后的子任务记录来源任务编码）',
  `main_order_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主生产订单id。指向：jvs_plan_task_order.order_id',
  `production_order_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所属生产订单id。指向：jvs_plan_task_order.order_id',
  `scheduled_quantity` decimal(18, 6) NULL DEFAULT NULL COMMENT '主产物计划生产数量',
  `process_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序编码',
  `process_info` json NULL COMMENT '工序信息',
  `front_task_codes` json NULL COMMENT '前置任务编码集合',
  `next_task_codes` json NULL COMMENT '后置任务编码集合',
  `main_resource_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主资源id。指向：jvs_production_resource.id',
  `start_time` datetime NULL DEFAULT NULL COMMENT '计划开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '计划完成时间',
  `start_task` tinyint(1) NULL DEFAULT NULL COMMENT '是否是工序任务链中的首道工序任务',
  `end_task` tinyint(1) NULL DEFAULT NULL COMMENT '是否是任务链中的最后一个任务',
  `supplement` tinyint(1) NULL DEFAULT NULL COMMENT '是否是补充生产任务',
  `pinned` tinyint(1) NULL DEFAULT NULL COMMENT '是否锁定任务',
  `merge_task` tinyint(1) NULL DEFAULT NULL COMMENT '是否是合并任务',
  `color` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示颜色',
  `input_materials` json NULL COMMENT '输入物料信息',
  `task_status` enum('PENDING','PARTIALLY_COMPLETED','COMPLETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务状态',
  `last_completion_time` datetime NULL DEFAULT NULL COMMENT '最近一次报工完成时间',
  `quantity_completed` decimal(18, 6) NULL DEFAULT NULL COMMENT '已完成数量',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_code`(`code` ASC) USING BTREE,
  INDEX `idx_order_id`(`production_order_id` ASC) USING BTREE,
  INDEX `idx_status`(`task_status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '排产计划任务' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_plan_task
-- ----------------------------

-- ----------------------------
-- Table structure for jvs_plan_task_adjust
-- ----------------------------
DROP TABLE IF EXISTS `jvs_plan_task_adjust`;
CREATE TABLE `jvs_plan_task_adjust`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务编码',
  `merge_task_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '合并任务的编码',
  `origin_task_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '源任务编码（拆分后的子任务记录来源任务编码）',
  `main_order_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主生产订单id。指向：jvs_plan_task_order.order_id',
  `production_order_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所属生产订单id。指向：jvs_plan_task_order.order_id',
  `scheduled_quantity` decimal(18, 6) NULL DEFAULT NULL COMMENT '主产物计划生产数量',
  `process_info` json NULL COMMENT '工序信息',
  `front_task_codes` json NULL COMMENT '前置任务编码集合',
  `next_task_codes` json NULL COMMENT '后置任务编码集合',
  `main_resource_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主资源id。指向：jvs_production_resource.id',
  `start_time` datetime NULL DEFAULT NULL COMMENT '计划开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '计划完成时间',
  `start_task` tinyint(1) NULL DEFAULT NULL COMMENT '是否是工序任务链中的首道工序任务',
  `end_task` tinyint(1) NULL DEFAULT NULL COMMENT '是否是任务链中的最后一个任务',
  `supplement` tinyint(1) NULL DEFAULT NULL COMMENT '是否是补充生产任务',
  `pinned` tinyint(1) NULL DEFAULT NULL COMMENT '是否锁定任务',
  `merge_task` tinyint(1) NULL DEFAULT NULL COMMENT '是否是合并任务',
  `color` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示颜色',
  `input_materials` json NULL COMMENT '输入物料信息',
  `task_status` enum('PENDING','PARTIALLY_COMPLETED','COMPLETED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务状态',
  `discard` tinyint(1) NULL DEFAULT NULL COMMENT '是否丢弃',
  `compliant` tinyint(1) NULL DEFAULT NULL COMMENT '任务是否合规',
  `last_completion_time` datetime NULL DEFAULT NULL COMMENT '最近一次报工完成时间',
  `quantity_completed` decimal(18, 6) NULL DEFAULT NULL COMMENT '已完成数量',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_code`(`code` ASC) USING BTREE,
  INDEX `idx_origin_task_code`(`origin_task_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '待处理的任务调整信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_plan_task_adjust
-- ----------------------------

-- ----------------------------
-- Table structure for jvs_plan_task_order
-- ----------------------------
DROP TABLE IF EXISTS `jvs_plan_task_order`;
CREATE TABLE `jvs_plan_task_order`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `order_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生产订单id。指向：jvs_production_order.id',
  `order_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生产订单编码',
  `delay_time_string` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单延期时长',
  `order_info` json NULL COMMENT '生产订单信息',
  `order_material_info` json NULL COMMENT '生产订单主产物信息',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_id`(`order_id` ASC) USING BTREE,
  UNIQUE INDEX `uk_order_code`(`order_code` ASC) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '排产计划任务冗余订单信息' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_plan_task_order
-- ----------------------------

-- ----------------------------
-- Table structure for jvs_plan_task_order_pending
-- ----------------------------
DROP TABLE IF EXISTS `jvs_plan_task_order_pending`;
CREATE TABLE `jvs_plan_task_order_pending`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `order_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生产订单id。指向：jvs_production_order.id',
  `order_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生产订单编码',
  `delay_time_string` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单延期时长',
  `order_info` json NULL COMMENT '生产订单信息',
  `order_material_info` json NULL COMMENT '生产订单主产物信息',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_order_id`(`order_id` ASC) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '待确认排产计划订单' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_plan_task_order_pending
-- ----------------------------

-- ----------------------------
-- Table structure for jvs_plan_task_pending
-- ----------------------------
DROP TABLE IF EXISTS `jvs_plan_task_pending`;
CREATE TABLE `jvs_plan_task_pending`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务编码',
  `merge_task_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '合并任务的编号',
  `origin_task_code` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '源任务编码（拆分后的子任务记录来源任务编码）',
  `main_order_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主生产订单id。指向：jvs_plan_task_order_pending.order_id',
  `production_order_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所属生产订单id。指向：jvs_plan_task_order_pending.order_id',
  `scheduled_quantity` decimal(18, 6) NULL DEFAULT NULL COMMENT '主产物计划生产数量',
  `process_info` json NULL COMMENT '工序信息',
  `front_task_codes` json NULL COMMENT '前置任务编码集合',
  `next_task_codes` json NULL COMMENT '后置任务编码集合',
  `main_resource_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '主资源id。指向：jvs_production_resource.id',
  `start_time` datetime NULL DEFAULT NULL COMMENT '计划开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '计划完成时间',
  `start_task` tinyint(1) NULL DEFAULT NULL COMMENT '是否是工序任务链中的首道工序任务',
  `end_task` tinyint(1) NULL DEFAULT NULL COMMENT '是否是任务链中的最后一个任务',
  `supplement` tinyint(1) NULL DEFAULT NULL COMMENT '是否是补充生产任务',
  `pinned` tinyint(1) NULL DEFAULT NULL COMMENT '是否锁定任务',
  `merge_task` tinyint(1) NULL DEFAULT NULL COMMENT '是否是合并任务',
  `color` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示颜色',
  `input_materials` json NULL COMMENT '输入物料',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '待确认排产计划任务' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_plan_task_pending
-- ----------------------------

-- ----------------------------
-- Table structure for jvs_planning_strategy
-- ----------------------------
DROP TABLE IF EXISTS `jvs_planning_strategy`;
CREATE TABLE `jvs_planning_strategy`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '策略名称',
  `begin_time` datetime NOT NULL COMMENT '开始时间',
  `active` tinyint(1) NULL DEFAULT NULL COMMENT '0-无效，1-有效',
  `config` json NULL COMMENT '策略配置',
  `order_scheduling_rules` json NULL COMMENT '排产规则',
  `optimize_rules` json NULL COMMENT '优化规则',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0未删除  1已删除',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '排产计划策略' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_planning_strategy
-- ----------------------------
INSERT INTO `jvs_planning_strategy` VALUES ('c5bab9f41c438449fa2af849940c357e', '汽车发动机', '2025-02-19 10:48:05', 1, '{\"direction\": \"FORWARD\", \"materialConstrained\": true, \"maxNoImprovementTime\": null}', '[{\"fieldKey\": \"priority\", \"sortRule\": \"DESC\", \"fieldName\": \"\"}, {\"fieldKey\": \"deliveryTime\", \"sortRule\": \"ASC\", \"fieldName\": \"\"}]', '[{\"weight\": 54, \"constraintKey\": \"scheduling_rules\"}, {\"weight\": 20, \"constraintKey\": \"minimize_tardiness\"}, {\"weight\": 1, \"constraintKey\": \"minimize_tardiness_task_number\"}, {\"weight\": 1, \"constraintKey\": \"prioritize_delivery_time\"}, {\"weight\": 0, \"constraintKey\": \"prioritize_short_task\"}, {\"weight\": 0, \"constraintKey\": \"minimize_slack_time\"}, {\"weight\": 0, \"constraintKey\": \"priority_critical_ratio\"}, {\"weight\": 1, \"constraintKey\": \"minimize_gap_between_dependent_tasks\"}, {\"weight\": 6, \"constraintKey\": \"finish_early\"}, {\"weight\": 15, \"constraintKey\": \"balance_task_count\"}, {\"weight\": 0, \"constraintKey\": \"balance_workload\"}]', 0, 'admin2', '1', '2025-02-19 10:48:29', 'admin2', '2025-02-19 11:35:49', '1');

-- ----------------------------
-- Table structure for jvs_process
-- ----------------------------
DROP TABLE IF EXISTS `jvs_process`;
CREATE TABLE `jvs_process`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名称',
  `code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '编码',
  `pre_interval_duration` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '前间隔时长',
  `post_interval_duration` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '后间隔时长',
  `process_relationship` enum('ES','EE') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序关系',
  `buffer_time` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关系为EE时的缓冲时长',
  `batch_strategy` json NULL COMMENT '批量策略',
  `use_main_resources` json NULL COMMENT '可用的主资源集合',
  `use_auxiliary_resources` json NULL COMMENT '可用的辅助资源集合',
  `use_materials` json NULL COMMENT '使用的物料',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0未删除  1已删除',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工艺' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_process
-- ----------------------------
INSERT INTO `jvs_process` VALUES ('14d0c6f185d7bb3e1aaf6bb79d39b22a', '汽车发动机-轴承', 'LX012', '60M', '60M', 'EE', NULL, NULL, '[{\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"ccc6981465ba673309ade7c458ab7c56\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"09937e6775ebad412a895f2bd7cd62ba\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"07d61cb277c22ad984fa06864a661c5b\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"ace5ee4b81135a7a74f79059472c5da8\", \"capacity\": 1, \"throughput\": \"\"}, {\"id\": \"d37501865094f8175168148857f529a8\", \"capacity\": 1, \"throughput\": \"\"}, {\"id\": \"144a156f6df1515f08cfbd876a4ac89a\", \"capacity\": 10, \"throughput\": \"\"}, {\"id\": \"8b6810c47ac9c037e42131829023b60a\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"cc3dc2c23e275d36e298cff4587e1398\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"fb7e44cf595effbbd28c76f9e4c533fd\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"\"}]', '[]', '[{\"id\": \"8fee5a4040ba738989ca9bbc8df91ad8\", \"use\": true, \"quantity\": 500}]', 0, 'admin2', '1', '2025-02-19 09:38:09', 'admin2', '2025-02-19 09:38:09', '1');
INSERT INTO `jvs_process` VALUES ('3a666dedcea05d7851cc0dd1af35b185', '发动机制作工序', 'LX001', '60M', '60M', 'ES', '30M', NULL, '[{\"id\": \"427b96b1d9550a6d9d3c3e967ebf9718\", \"capacity\": 4, \"throughput\": \"\"}, {\"id\": \"ccc6981465ba673309ade7c458ab7c56\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"5386a0396342a4a1b7e6bef175d913e3\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"09937e6775ebad412a895f2bd7cd62ba\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"d37501865094f8175168148857f529a8\", \"capacity\": 1, \"throughput\": \"\"}, {\"id\": \"07d61cb277c22ad984fa06864a661c5b\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"ace5ee4b81135a7a74f79059472c5da8\", \"capacity\": 1, \"throughput\": \"\"}, {\"id\": \"0b3c6b46b4ce9e2b987ee7f0c587f9ba\", \"capacity\": 1, \"throughput\": \"\"}, {\"id\": \"144a156f6df1515f08cfbd876a4ac89a\", \"capacity\": 10, \"throughput\": \"\"}, {\"id\": \"8b6810c47ac9c037e42131829023b60a\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"cc3dc2c23e275d36e298cff4587e1398\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"87774469924fbca891ace974a3820f4b\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"fb7e44cf595effbbd28c76f9e4c533fd\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"eb2f6301ac121856983eb696f49c653a\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"a8442fe8f6f47a83455c277245bbdb16\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"\"}]', '[]', '[{\"id\": \"327d114c3a7f5a9d01cca2dbaeb5b9dd\", \"use\": true, \"quantity\": 1}, {\"id\": \"291191f4c070b2bdb5f185b63ea366ce\", \"use\": true, \"quantity\": 500}, {\"id\": \"8fee5a4040ba738989ca9bbc8df91ad8\", \"use\": true, \"quantity\": 500}, {\"id\": \"e320bcad3ce32360902350c53bb42049\", \"use\": true, \"quantity\": 500}, {\"id\": \"861fe861d34ae7b6cd840275df7deb25\", \"use\": true, \"quantity\": 500}, {\"id\": \"7ceb314ae11af93c7bfaca2200b10148\", \"use\": true, \"quantity\": 500}, {\"id\": \"18c92cc507fd2824be22ef4a9db11116\", \"use\": true, \"quantity\": 500}, {\"id\": \"8ef50c2463a8cfb07d6a99dc7aec49c2\", \"use\": true, \"quantity\": 500}, {\"id\": \"7b576af9247c2d2278b135b8e8c546ba\", \"use\": true, \"quantity\": 500}, {\"id\": \"dcb347ab06cc3a88981ec5aea0fbec8e\", \"use\": true, \"quantity\": 500}, {\"id\": \"893d901a0d3ca3a75e201be530467036\", \"use\": true, \"quantity\": 500}, {\"id\": \"8c19bf73f842d3eeee3f02e4666e077b\", \"use\": true, \"quantity\": 500}]', 0, 'admin2', '1', '2025-02-18 11:38:33', 'admin2', '2025-02-18 11:38:33', '1');
INSERT INTO `jvs_process` VALUES ('4687576ce979acd2b222cdb421ed2ee2', '汽车发动机-气门', 'LX007', '60M', '60M', 'ES', NULL, NULL, '[{\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"5386a0396342a4a1b7e6bef175d913e3\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"144a156f6df1515f08cfbd876a4ac89a\", \"capacity\": 10, \"throughput\": \"\"}]', '[]', '[{\"id\": \"7ceb314ae11af93c7bfaca2200b10148\", \"use\": true, \"quantity\": 500}]', 0, 'admin2', '1', '2025-02-19 09:30:10', 'admin2', '2025-02-19 09:30:10', '1');
INSERT INTO `jvs_process` VALUES ('585e7053dd4c8862891aa40c2fe0494b', '汽车发动机-连杆', 'LX005', '60M', '60M', 'EE', NULL, NULL, '[{\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"d37501865094f8175168148857f529a8\", \"capacity\": 1, \"throughput\": \"\"}, {\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"\"}]', '[]', '[{\"id\": \"7b576af9247c2d2278b135b8e8c546ba\", \"use\": true, \"quantity\": 500}]', 0, 'admin2', '1', '2025-02-19 09:22:43', 'admin2', '2025-02-19 09:22:43', '1');
INSERT INTO `jvs_process` VALUES ('60f585113d3d984168daa0ed304d3368', '汽车发动机-油管', 'LX009', '60M', '60M', 'ES', NULL, NULL, '[{\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"ccc6981465ba673309ade7c458ab7c56\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"5386a0396342a4a1b7e6bef175d913e3\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"87774469924fbca891ace974a3820f4b\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"8b6810c47ac9c037e42131829023b60a\", \"capacity\": 3, \"throughput\": \"\"}]', '[]', '[{\"id\": \"18c92cc507fd2824be22ef4a9db11116\", \"use\": true, \"quantity\": 500}]', 0, 'admin2', '1', '2025-02-19 09:35:11', 'admin2', '2025-02-19 09:35:11', '1');
INSERT INTO `jvs_process` VALUES ('973e04c59db4c048a35bc0e566d68abc', '汽车发动机-缸体', 'LX002', '60M', '60M', 'ES', NULL, NULL, '[{\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"\"}]', '[]', '[{\"id\": \"dcb347ab06cc3a88981ec5aea0fbec8e\", \"use\": true, \"quantity\": 500}]', 0, 'admin2', '1', '2025-02-19 09:18:14', 'admin2', '2025-02-19 09:19:34', '1');
INSERT INTO `jvs_process` VALUES ('976dd3951c3c6de145e6d35e3657ecfc', '汽车发动机-曲轴', 'LX004', '60M', '60M', 'ES', NULL, NULL, '[{\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"a8442fe8f6f47a83455c277245bbdb16\", \"capacity\": 3, \"throughput\": \"\"}]', '[]', '[{\"id\": \"893d901a0d3ca3a75e201be530467036\", \"use\": true, \"quantity\": 500}]', 0, 'admin2', '1', '2025-02-19 09:20:54', 'admin2', '2025-02-19 10:41:32', '1');
INSERT INTO `jvs_process` VALUES ('97ae1b8a918cc486d964de028453498f', '汽车发动机-密封件', 'LX011', '60M', '60M', 'ES', NULL, NULL, '[{\"id\": \"427b96b1d9550a6d9d3c3e967ebf9718\", \"capacity\": 4, \"throughput\": \"\"}, {\"id\": \"ccc6981465ba673309ade7c458ab7c56\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"87774469924fbca891ace974a3820f4b\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"fb7e44cf595effbbd28c76f9e4c533fd\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"\"}]', '[]', '[{\"id\": \"291191f4c070b2bdb5f185b63ea366ce\", \"use\": true, \"quantity\": 500}]', 0, 'admin2', '1', '2025-02-19 09:37:02', 'admin2', '2025-02-19 09:37:02', '1');
INSERT INTO `jvs_process` VALUES ('98083d095c1a667beef9d42b85d56859', '汽车发动机-缸盖', 'LX003', '60M', '60M', 'EE', NULL, NULL, '[{\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"d37501865094f8175168148857f529a8\", \"capacity\": 1, \"throughput\": \"\"}, {\"id\": \"cc3dc2c23e275d36e298cff4587e1398\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"\"}]', '[]', '[{\"id\": \"8c19bf73f842d3eeee3f02e4666e077b\", \"use\": true, \"quantity\": 500}]', 0, 'admin2', '1', '2025-02-19 09:19:26', 'admin2', '2025-02-19 09:19:26', '1');
INSERT INTO `jvs_process` VALUES ('a0faffa616a1a7c83c275f5a875c10d0', '汽车发动机-水泵', 'LX010', '60M', '60M', 'EE', NULL, NULL, '[{\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"09937e6775ebad412a895f2bd7cd62ba\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"5386a0396342a4a1b7e6bef175d913e3\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"144a156f6df1515f08cfbd876a4ac89a\", \"capacity\": 10, \"throughput\": \"\"}, {\"id\": \"87774469924fbca891ace974a3820f4b\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"cc3dc2c23e275d36e298cff4587e1398\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"fb7e44cf595effbbd28c76f9e4c533fd\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"\"}]', '[]', '[{\"id\": \"e320bcad3ce32360902350c53bb42049\", \"use\": true, \"quantity\": 500}]', 0, 'admin2', '1', '2025-02-19 09:36:01', 'admin2', '2025-02-19 09:36:01', '1');
INSERT INTO `jvs_process` VALUES ('d0d706329686c92d6ca3e7ea87d91ccf', '汽车发动机-活塞', 'LX006', '60M', '60M', 'ES', NULL, NULL, '[{\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"09937e6775ebad412a895f2bd7cd62ba\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"5386a0396342a4a1b7e6bef175d913e3\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"8b6810c47ac9c037e42131829023b60a\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"a8442fe8f6f47a83455c277245bbdb16\", \"capacity\": 3, \"throughput\": \"\"}]', '[]', '[]', 0, 'admin2', '1', '2025-02-19 09:23:47', 'admin2', '2025-02-19 09:23:47', '1');
INSERT INTO `jvs_process` VALUES ('e993a1de64159ba59d52761cdd25fd90', '汽车发动机-进排气歧管', 'LX008', '60M', '60M', 'EE', NULL, NULL, '[{\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"\"}, {\"id\": \"144a156f6df1515f08cfbd876a4ac89a\", \"capacity\": 10, \"throughput\": \"\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"a8442fe8f6f47a83455c277245bbdb16\", \"capacity\": 3, \"throughput\": \"\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"\"}]', '[]', '[{\"id\": \"861fe861d34ae7b6cd840275df7deb25\", \"use\": true, \"quantity\": 500}]', 0, 'admin2', '1', '2025-02-19 09:34:09', 'admin2', '2025-02-19 09:34:09', '1');

-- ----------------------------
-- Table structure for jvs_process_route
-- ----------------------------
DROP TABLE IF EXISTS `jvs_process_route`;
CREATE TABLE `jvs_process_route`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `material_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料id。指向：jvs_material.id',
  `route_design` json NULL COMMENT '工艺路线设计',
  `enabled` tinyint(1) NULL DEFAULT NULL COMMENT '0-未启用，1-启用',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0未删除  1已删除',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工艺路线' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_process_route
-- ----------------------------
INSERT INTO `jvs_process_route` VALUES ('92e7ed7c52e8593b1ac1553e4d9584cf', '327d114c3a7f5a9d01cca2dbaeb5b9dd', '{\"edges\": [{\"source\": \"00e02507-1c06-496c-8b23-b296ce737603\", \"target\": \"6db04f09-302e-4aac-a8ee-e8af2f84c9f1\"}, {\"source\": \"bc7673c8-6279-48ad-a639-78eb8b63fc13\", \"target\": \"6db04f09-302e-4aac-a8ee-e8af2f84c9f1\"}, {\"source\": \"0504856b-9f4d-40b2-a086-6f997ac1927b\", \"target\": \"e75e45dc-b819-431b-a90b-cb99294dbf7d\"}, {\"source\": \"e7550a64-b35f-4d7a-9565-8425a6b34d61\", \"target\": \"6db04f09-302e-4aac-a8ee-e8af2f84c9f1\"}, {\"source\": \"4a75d8e0-dc12-4d71-b0ce-0b400c07ce70\", \"target\": \"fce44dc2-37ed-407d-b321-04d70d161711\"}, {\"source\": \"fce44dc2-37ed-407d-b321-04d70d161711\", \"target\": \"6db04f09-302e-4aac-a8ee-e8af2f84c9f1\"}, {\"source\": \"e75e45dc-b819-431b-a90b-cb99294dbf7d\", \"target\": \"6db04f09-302e-4aac-a8ee-e8af2f84c9f1\"}, {\"source\": \"aa03d668-72ac-48f9-9cdc-176a0205b8bc\", \"target\": \"6db04f09-302e-4aac-a8ee-e8af2f84c9f1\"}, {\"source\": \"15acd926-1920-435e-9463-69c467b347f6\", \"target\": \"6db04f09-302e-4aac-a8ee-e8af2f84c9f1\"}, {\"source\": \"20ddcbd7-21d7-4a61-bc3c-f77a04eb013a\", \"target\": \"6db04f09-302e-4aac-a8ee-e8af2f84c9f1\"}, {\"source\": \"0564b0f3-a6d2-4241-8e6d-3b892163c172\", \"target\": \"fce44dc2-37ed-407d-b321-04d70d161711\"}], \"nodes\": [{\"id\": \"e7550a64-b35f-4d7a-9565-8425a6b34d61\", \"data\": {\"code\": \"LX003\", \"name\": \"汽车发动机-缸盖\", \"bufferTime\": \"60M\", \"useMaterials\": [{\"id\": \"8c19bf73f842d3eeee3f02e4666e077b\", \"use\": true, \"quantity\": 500}], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"d37501865094f8175168148857f529a8\", \"capacity\": 1, \"throughput\": \"12MP\"}, {\"id\": \"cc3dc2c23e275d36e298cff4587e1398\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"EE\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": -680.9222593925956, \"y\": -96.70359784794684, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 24}}, {\"id\": \"6db04f09-302e-4aac-a8ee-e8af2f84c9f1\", \"data\": {\"code\": \"LX001\", \"name\": \"发动机制作工序\", \"bufferTime\": \"30M\", \"useMaterials\": [{\"id\": \"327d114c3a7f5a9d01cca2dbaeb5b9dd\", \"use\": true, \"quantity\": 1}, {\"id\": \"291191f4c070b2bdb5f185b63ea366ce\", \"use\": true, \"quantity\": 500}, {\"id\": \"8fee5a4040ba738989ca9bbc8df91ad8\", \"use\": true, \"quantity\": 500}, {\"id\": \"e320bcad3ce32360902350c53bb42049\", \"use\": true, \"quantity\": 500}, {\"id\": \"861fe861d34ae7b6cd840275df7deb25\", \"use\": true, \"quantity\": 500}, {\"id\": \"7ceb314ae11af93c7bfaca2200b10148\", \"use\": true, \"quantity\": 500}, {\"id\": \"18c92cc507fd2824be22ef4a9db11116\", \"use\": true, \"quantity\": 500}, {\"id\": \"8ef50c2463a8cfb07d6a99dc7aec49c2\", \"use\": true, \"quantity\": 500}, {\"id\": \"7b576af9247c2d2278b135b8e8c546ba\", \"use\": true, \"quantity\": 500}, {\"id\": \"dcb347ab06cc3a88981ec5aea0fbec8e\", \"use\": true, \"quantity\": 500}, {\"id\": \"893d901a0d3ca3a75e201be530467036\", \"use\": true, \"quantity\": 500}, {\"id\": \"8c19bf73f842d3eeee3f02e4666e077b\", \"use\": true, \"quantity\": 500}], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"427b96b1d9550a6d9d3c3e967ebf9718\", \"capacity\": 4, \"throughput\": \"12MP\"}, {\"id\": \"ccc6981465ba673309ade7c458ab7c56\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"5386a0396342a4a1b7e6bef175d913e3\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"09937e6775ebad412a895f2bd7cd62ba\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"d37501865094f8175168148857f529a8\", \"capacity\": 1, \"throughput\": \"12MP\"}, {\"id\": \"07d61cb277c22ad984fa06864a661c5b\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"ace5ee4b81135a7a74f79059472c5da8\", \"capacity\": 1, \"throughput\": \"12MP\"}, {\"id\": \"0b3c6b46b4ce9e2b987ee7f0c587f9ba\", \"capacity\": 1, \"throughput\": \"12MP\"}, {\"id\": \"144a156f6df1515f08cfbd876a4ac89a\", \"capacity\": 10, \"throughput\": \"12MP\"}, {\"id\": \"8b6810c47ac9c037e42131829023b60a\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"cc3dc2c23e275d36e298cff4587e1398\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"87774469924fbca891ace974a3820f4b\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"fb7e44cf595effbbd28c76f9e4c533fd\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"eb2f6301ac121856983eb696f49c653a\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"a8442fe8f6f47a83455c277245bbdb16\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"ES\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": -202.3897055340899, \"y\": -43.374609917453114, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 35}}, {\"id\": \"00e02507-1c06-496c-8b23-b296ce737603\", \"data\": {\"code\": \"LX005\", \"name\": \"汽车发动机-连杆\", \"bufferTime\": \"60M\", \"useMaterials\": [{\"id\": \"7b576af9247c2d2278b135b8e8c546ba\", \"use\": true, \"quantity\": 500}], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"d37501865094f8175168148857f529a8\", \"capacity\": 1, \"throughput\": \"12MP\"}, {\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"EE\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": -185.97291123157055, \"y\": 177.96007099323376, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 43}}, {\"id\": \"bc7673c8-6279-48ad-a639-78eb8b63fc13\", \"data\": {\"code\": \"LX002\", \"name\": \"汽车发动机-缸体\", \"bufferTime\": \"\", \"useMaterials\": [{\"id\": \"dcb347ab06cc3a88981ec5aea0fbec8e\", \"use\": true, \"quantity\": 500}], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"ES\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": -676.7999877929688, \"y\": -202.3999938964844, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 41}}, {\"id\": \"15acd926-1920-435e-9463-69c467b347f6\", \"data\": {\"code\": \"LX006\", \"name\": \"汽车发动机-活塞\", \"bufferTime\": \"\", \"useMaterials\": [], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"09937e6775ebad412a895f2bd7cd62ba\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"5386a0396342a4a1b7e6bef175d913e3\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"8b6810c47ac9c037e42131829023b60a\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"a8442fe8f6f47a83455c277245bbdb16\", \"capacity\": 3, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"ES\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": -216.2204678374155, \"y\": -269.26383596464825, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 42}}, {\"id\": \"aa03d668-72ac-48f9-9cdc-176a0205b8bc\", \"data\": {\"code\": \"LX004\", \"name\": \"汽车发动机-曲轴\", \"bufferTime\": \"\", \"useMaterials\": [{\"id\": \"893d901a0d3ca3a75e201be530467036\", \"use\": true, \"quantity\": 500}], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"a8442fe8f6f47a83455c277245bbdb16\", \"capacity\": 3, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"ES\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": 113.5999755859375, \"y\": 170.39996337890625, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 36}}, {\"id\": \"fce44dc2-37ed-407d-b321-04d70d161711\", \"data\": {\"code\": \"LX009\", \"name\": \"汽车发动机-油管\", \"bufferTime\": \"\", \"useMaterials\": [{\"id\": \"18c92cc507fd2824be22ef4a9db11116\", \"use\": true, \"quantity\": 500}], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"ccc6981465ba673309ade7c458ab7c56\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"5386a0396342a4a1b7e6bef175d913e3\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"87774469924fbca891ace974a3820f4b\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"8b6810c47ac9c037e42131829023b60a\", \"capacity\": 3, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"ES\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": -424.8194452674913, \"y\": 61.630278932066766, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 28}}, {\"id\": \"e75e45dc-b819-431b-a90b-cb99294dbf7d\", \"data\": {\"code\": \"LX011\", \"name\": \"汽车发动机-密封件\", \"bufferTime\": \"\", \"useMaterials\": [{\"id\": \"291191f4c070b2bdb5f185b63ea366ce\", \"use\": true, \"quantity\": 500}], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"427b96b1d9550a6d9d3c3e967ebf9718\", \"capacity\": 4, \"throughput\": \"12MP\"}, {\"id\": \"ccc6981465ba673309ade7c458ab7c56\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"87774469924fbca891ace974a3820f4b\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"fb7e44cf595effbbd28c76f9e4c533fd\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"ES\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": 90.84731013667432, \"y\": -194.47260120351112, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 33}}, {\"id\": \"0564b0f3-a6d2-4241-8e6d-3b892163c172\", \"data\": {\"code\": \"LX008\", \"name\": \"汽车发动机-进排气歧管\", \"bufferTime\": \"60M\", \"useMaterials\": [{\"id\": \"861fe861d34ae7b6cd840275df7deb25\", \"use\": true, \"quantity\": 500}], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"144a156f6df1515f08cfbd876a4ac89a\", \"capacity\": 10, \"throughput\": \"12MP\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"a8442fe8f6f47a83455c277245bbdb16\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"8bbbe1aedbab73693576bcf387269863\", \"capacity\": 3, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"EE\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": -683.517725197841, \"y\": 96.23881103698888, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 27}}, {\"id\": \"0504856b-9f4d-40b2-a086-6f997ac1927b\", \"data\": {\"code\": \"LX012\", \"name\": \"汽车发动机-轴承\", \"bufferTime\": \"60M\", \"useMaterials\": [{\"id\": \"8fee5a4040ba738989ca9bbc8df91ad8\", \"use\": true, \"quantity\": 500}], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"ccc6981465ba673309ade7c458ab7c56\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"5ddff183e464b785940a46bfb42629c1\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"09937e6775ebad412a895f2bd7cd62ba\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"07d61cb277c22ad984fa06864a661c5b\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"ace5ee4b81135a7a74f79059472c5da8\", \"capacity\": 1, \"throughput\": \"12MP\"}, {\"id\": \"d37501865094f8175168148857f529a8\", \"capacity\": 1, \"throughput\": \"12MP\"}, {\"id\": \"144a156f6df1515f08cfbd876a4ac89a\", \"capacity\": 10, \"throughput\": \"12MP\"}, {\"id\": \"8b6810c47ac9c037e42131829023b60a\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"cc3dc2c23e275d36e298cff4587e1398\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"fb7e44cf595effbbd28c76f9e4c533fd\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"90f6585fbf652f6e4d6dfa22ffabc1c3\", \"capacity\": 3, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"EE\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": 389.599853515625, \"y\": -192.79998779296875, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 40}}, {\"id\": \"20ddcbd7-21d7-4a61-bc3c-f77a04eb013a\", \"data\": {\"code\": \"LX010\", \"name\": \"汽车发动机-水泵\", \"bufferTime\": \"60M\", \"useMaterials\": [{\"id\": \"e320bcad3ce32360902350c53bb42049\", \"use\": true, \"quantity\": 500}], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"09937e6775ebad412a895f2bd7cd62ba\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"5386a0396342a4a1b7e6bef175d913e3\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"144a156f6df1515f08cfbd876a4ac89a\", \"capacity\": 10, \"throughput\": \"12MP\"}, {\"id\": \"87774469924fbca891ace974a3820f4b\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"cc3dc2c23e275d36e298cff4587e1398\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"fb7e44cf595effbbd28c76f9e4c533fd\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"EE\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": 121.12987886984584, \"y\": -23.160471250641912, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 34}}, {\"id\": \"4a75d8e0-dc12-4d71-b0ce-0b400c07ce70\", \"data\": {\"code\": \"LX007\", \"name\": \"汽车发动机-气门\", \"bufferTime\": \"\", \"useMaterials\": [{\"id\": \"7ceb314ae11af93c7bfaca2200b10148\", \"use\": true, \"quantity\": 500}], \"batchStrategy\": null, \"useMainResources\": [{\"id\": \"c3196b84c967db67528aa26916460aa2\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"5386a0396342a4a1b7e6bef175d913e3\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"223ff4449c29629b18923df168d66bf7\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"73b44a05015121211f9f40d4d1067d5a\", \"capacity\": 5, \"throughput\": \"12MP\"}, {\"id\": \"0be18a0b8d9359365803ee7c4e453551\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"cba806b581ffe1493ca3154c2b8a491d\", \"capacity\": 3, \"throughput\": \"12MP\"}, {\"id\": \"144a156f6df1515f08cfbd876a4ac89a\", \"capacity\": 10, \"throughput\": \"12MP\"}], \"preIntervalDuration\": \"60M\", \"processRelationship\": \"ES\", \"postIntervalDuration\": \"60M\", \"useAuxiliaryResources\": []}, \"style\": {\"x\": -680.0569941067254, \"y\": 0.20017322698300344, \"z\": 0, \"cursor\": \"pointer\", \"zIndex\": 26}}]}', 1, 0, 'admin2', '1', '2025-02-19 09:09:29', 'admin2', '2025-02-19 14:36:42', '1');

-- ----------------------------
-- Table structure for jvs_production_order
-- ----------------------------
DROP TABLE IF EXISTS `jvs_production_order`;
CREATE TABLE `jvs_production_order`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单编码',
  `material_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '物料编码',
  `quantity` decimal(18, 6) NULL DEFAULT NULL COMMENT '需求数量',
  `delivery_time` datetime NULL DEFAULT NULL COMMENT '需求交付时间',
  `priority` int NULL DEFAULT NULL COMMENT '优先级',
  `sequence` decimal(18, 6) NULL DEFAULT NULL COMMENT '序号',
  `type` enum('MANUFACTURE') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单类型',
  `order_status` enum('PENDING','COMPLETED','CANCELLED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单状态',
  `scheduling_status` enum('UNSCHEDULED','SCHEDULED','COMPLETED','NO_SCHEDULED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '排产状态',
  `color` char(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示颜色',
  `can_schedule` tinyint(1) NULL DEFAULT 1 COMMENT '是否参与排产。0-否，1-是',
  `supplement` tinyint(1) NULL DEFAULT NULL COMMENT '是否为补充生产订单。0-否，1-是',
  `parent_order_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '父订单编码（如果是补充订单，则指向其是那个订单的补充订单',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0未删除  1已删除',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE,
  INDEX `uk_code`(`code` ASC) USING BTREE,
  INDEX `idx_parent_code`(`parent_order_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '生产订单' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_production_order
-- ----------------------------
INSERT INTO `jvs_production_order` VALUES ('38bccd6644d631189bc25990d4b5bb93', 'LX001', 'L0012', 100.000000, '2025-05-10 00:00:00', 4, 2.992188, 'MANUFACTURE', 'PENDING', 'SCHEDULED', '#FFD700', 1, 0, NULL, 0, 'admin2', '1', '2025-02-19 10:49:44', 'admin2', '2025-02-19 14:37:05', '1');

-- ----------------------------
-- Table structure for jvs_production_resource
-- ----------------------------
DROP TABLE IF EXISTS `jvs_production_resource`;
CREATE TABLE `jvs_production_resource`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名称',
  `code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '编码',
  `resource_group` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源组',
  `capacity` decimal(18, 6) NULL DEFAULT NULL COMMENT '容量',
  `unit` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '容量计量单位',
  `throughput` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产能',
  `resource_status` enum('NORMAL','MAINTENANCE','SCRAPPED') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源状态',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0未删除  1已删除',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '主生产资源' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_production_resource
-- ----------------------------
INSERT INTO `jvs_production_resource` VALUES ('07d61cb277c22ad984fa06864a661c5b', '汽车发动机设备-电阻炉', 'LX014', '烤箱', 3.000000, '吨', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:40:26', 'admin2', '2025-02-18 10:40:26', '1');
INSERT INTO `jvs_production_resource` VALUES ('09937e6775ebad412a895f2bd7cd62ba', '汽车发动机设备-设计师', 'LX016', '人力组', 3.000000, '人', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:50:45', 'admin2', '2025-02-18 10:50:45', '1');
INSERT INTO `jvs_production_resource` VALUES ('0b3c6b46b4ce9e2b987ee7f0c587f9ba', '汽车发动机设备-超声波探伤仪', 'LX012', '质检', 1.000000, '件', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:29:47', 'admin2', '2025-02-18 10:29:59', '1');
INSERT INTO `jvs_production_resource` VALUES ('0be18a0b8d9359365803ee7c4e453551', '汽车发动机设备-浇注机', 'LX006', '打磨机', 3.000000, '千克', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:18:50', 'admin2', '2025-02-18 10:18:50', '1');
INSERT INTO `jvs_production_resource` VALUES ('144a156f6df1515f08cfbd876a4ac89a', '汽车发动机设备-游标卡尺', 'LX011', '未分组', 10.000000, 'cm', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:29:11', 'admin2', '2025-02-18 10:29:11', '1');
INSERT INTO `jvs_production_resource` VALUES ('223ff4449c29629b18923df168d66bf7', '汽车发动机设备-锻造工', 'LX018', '人力组', 3.000000, '名', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 11:09:44', 'admin2', '2025-02-18 11:09:44', '1');
INSERT INTO `jvs_production_resource` VALUES ('427b96b1d9550a6d9d3c3e967ebf9718', '汽车发动机设备-包装人员', 'LX023', '人力组', 4.000000, '人', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 11:13:10', 'admin2', '2025-02-18 11:13:10', '1');
INSERT INTO `jvs_production_resource` VALUES ('5386a0396342a4a1b7e6bef175d913e3', '汽车发动机设备-机加工人员', 'LX019', '人力组', 5.000000, '人', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 11:10:15', 'admin2', '2025-02-18 11:10:15', '1');
INSERT INTO `jvs_production_resource` VALUES ('5ddff183e464b785940a46bfb42629c1', '汽车发动机设备-热处理工', 'LX020', '人力组', 3.000000, '人', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 11:10:43', 'admin2', '2025-02-18 11:10:43', '1');
INSERT INTO `jvs_production_resource` VALUES ('73b44a05015121211f9f40d4d1067d5a', '汽车发动机设备-工程师', 'LX017', '人力组', 5.000000, '人', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:51:15', 'admin2', '2025-02-18 10:51:15', '1');
INSERT INTO `jvs_production_resource` VALUES ('87774469924fbca891ace974a3820f4b', '汽车发动机设备-锯片铣床', 'LX008', '切割机', 3.000000, '件', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:22:42', 'admin2', '2025-02-18 10:22:42', '1');
INSERT INTO `jvs_production_resource` VALUES ('8b6810c47ac9c037e42131829023b60a', '汽车发动机设备-喷砂机', 'LX010', '喷漆机', 3.000000, '件', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:27:26', 'admin2', '2025-02-18 10:27:26', '1');
INSERT INTO `jvs_production_resource` VALUES ('8bbbe1aedbab73693576bcf387269863', '汽车发动机设备-熔炼炉', 'LX001', '烤箱', 3.000000, '吨', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:13:46', 'admin2', '2025-02-18 10:13:46', '1');
INSERT INTO `jvs_production_resource` VALUES ('90f6585fbf652f6e4d6dfa22ffabc1c3', '汽车发动机设备-低压铸造机', 'LX005', '焊接', 3.000000, '件', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:18:00', 'admin2', '2025-02-18 10:18:00', '1');
INSERT INTO `jvs_production_resource` VALUES ('a8442fe8f6f47a83455c277245bbdb16', '汽车发动机设备-造型机', 'LX002', '切割机', 3.000000, '件', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:14:53', 'admin2', '2025-02-18 10:14:53', '1');
INSERT INTO `jvs_production_resource` VALUES ('ace5ee4b81135a7a74f79059472c5da8', '汽车发动机设备-硬度测试仪', 'LX013', '质检', 1.000000, '件', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:31:28', 'admin2', '2025-02-18 10:31:28', '1');
INSERT INTO `jvs_production_resource` VALUES ('c3196b84c967db67528aa26916460aa2', '汽车发动机设备-装配工', 'LX021', '人力组', 3.000000, '人', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 11:11:14', 'admin2', '2025-02-18 11:11:14', '1');
INSERT INTO `jvs_production_resource` VALUES ('cba806b581ffe1493ca3154c2b8a491d', '汽车发动机设备-压铸机', 'LX004', '焊接', 3.000000, '件', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:17:32', 'admin2', '2025-02-18 10:17:32', '1');
INSERT INTO `jvs_production_resource` VALUES ('cc3dc2c23e275d36e298cff4587e1398', '汽车发动机设备-抛光机', 'LX009', '打磨机', 3.000000, '件', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:26:38', 'admin2', '2025-02-18 10:26:38', '1');
INSERT INTO `jvs_production_resource` VALUES ('ccc6981465ba673309ade7c458ab7c56', '汽车发动机设备-质检人员', 'LX022', '人力组', 3.000000, '人', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 11:11:43', 'admin2', '2025-02-18 11:11:43', '1');
INSERT INTO `jvs_production_resource` VALUES ('d37501865094f8175168148857f529a8', '汽车发动机设备-淬火槽', 'LX015', '未分组', 1.000000, '吨', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:49:17', 'admin2', '2025-02-18 10:49:17', '1');
INSERT INTO `jvs_production_resource` VALUES ('eb2f6301ac121856983eb696f49c653a', '汽车发动机设备-混砂机', 'LX003', '喷漆机', 3.000000, '件', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:16:35', 'admin2', '2025-02-18 10:16:35', '1');
INSERT INTO `jvs_production_resource` VALUES ('fb7e44cf595effbbd28c76f9e4c533fd', '汽车发动机设备-冷却塔', 'LX007', '未分组', 3.000000, '件', NULL, 'NORMAL', 0, 'admin2', '1', '2025-02-18 10:22:00', 'admin2', '2025-02-18 10:22:00', '1');

-- ----------------------------
-- Table structure for jvs_resource_calendar
-- ----------------------------
DROP TABLE IF EXISTS `jvs_resource_calendar`;
CREATE TABLE `jvs_resource_calendar`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `work_calendar_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工作日历id。指向：jvs_work_calendar.id',
  `production_resource_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '资源id。指向：jvs_production_resource.id',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_work_calendar_id`(`work_calendar_id` ASC) USING BTREE,
  INDEX `idx_production_resource_id`(`production_resource_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '资源_日历关系' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_resource_calendar
-- ----------------------------

-- ----------------------------
-- Table structure for jvs_work_calendar
-- ----------------------------
DROP TABLE IF EXISTS `jvs_work_calendar`;
CREATE TABLE `jvs_work_calendar`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名称',
  `work_mode_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工作模式id。指向：jvs_work_mode.id',
  `begin_time` datetime NULL DEFAULT NULL COMMENT '起始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '截止时间',
  `enabled` tinyint(1) NULL DEFAULT NULL COMMENT '是否启用 0-未启用，1-启用',
  `work_days` bit(7) NULL DEFAULT NULL COMMENT '工作日设置',
  `priority` int NULL DEFAULT NULL COMMENT '优先级',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0未删除  1已删除',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工作日历' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_work_calendar
-- ----------------------------

-- ----------------------------
-- Table structure for jvs_work_mode
-- ----------------------------
DROP TABLE IF EXISTS `jvs_work_mode`;
CREATE TABLE `jvs_work_mode`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名称',
  `working_mode` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工作模式',
  `del_flag` tinyint(1) NULL DEFAULT 0 COMMENT '是否删除 0未删除  1已删除',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '工作模式' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_work_mode
-- ----------------------------

-- ----------------------------
-- Table structure for jvs_work_report
-- ----------------------------
DROP TABLE IF EXISTS `jvs_work_report`;
CREATE TABLE `jvs_work_report`  (
  `id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '主键',
  `report_time` datetime NULL DEFAULT NULL COMMENT '报工时间',
  `order_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单id',
  `order_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '订单编码',
  `material_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '产品编码',
  `process_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '工序编码',
  `plan_resource_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '计划主资源编码',
  `plan_resource_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '计划主资源id',
  `plan_start_time` datetime NULL DEFAULT NULL COMMENT '计划开始时间',
  `plan_end_time` datetime NULL DEFAULT NULL COMMENT '计划结束时间',
  `start_time` datetime NULL DEFAULT NULL COMMENT '实际开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '实际完成时间',
  `quantity_completed` decimal(18, 6) NULL DEFAULT NULL COMMENT '完成数量',
  `resource_code` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '实际主资源编码',
  `resource_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '实际主资源id',
  `create_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者姓名',
  `create_by_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者id',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '修改人姓名',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `tenant_id` char(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '租户id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_tenant_id`(`tenant_id` ASC) USING BTREE,
  INDEX `idx_order_code`(`order_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '报工记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of jvs_work_report
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;

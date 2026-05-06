# JVS-APS 系列文章目录

本目录基于 `style` 目录中的 7 篇样本文稿写作方式整理，围绕当前仓库中的 `jvs-aps` 系统，形成一套新的章节化技术文章。整套文章遵循同一条主线：

```
业务问题
   ↓
基础数据与领域建模
   ↓
求解器配置与排产策略
   ↓
前向/逆向时间计算
   ↓
约束评分与动态权重
   ↓
甘特图交互与人工调整
   ↓
生产落地与后续演进
```

## 目录

1. `01_system_introduction.md`
   JVS-APS 的业务背景、系统目标与总体架构
2. `02_domain_data_model.md`
   从订单、BOM、工艺路线到排产实体的建模过程
3. `03_solver_architecture.md`
   求解器装配、策略模板与排产流程
4. `04_time_calculation_forward_backward.md`
   正排、逆排、影子变量与时间推导机制
5. `05_constraint_scoring_and_strategy.md`
   约束体系、分数模型与动态权重配置
6. `06_human_in_loop_gantt_and_adjustment.md`
   甘特图、预览、确认、锁定、拆分、移动的人机协同闭环
7. `07_production_deployment_and_evolution.md`
   生产落地、运行治理与系统演进路线
8. `08_production_operations_manual.md`
   面向实施、计划与运维的生产操作手册
9. `09_business_planner_manual.md`
   面向业务与计划员的日常操作版
10. `10_implementation_operations_manual.md`
   面向实施与运维的部署上线版
11. `11_developer_handover_manual.md`
   面向开发接手的架构与接口版
12. `12_multitenant_rbac_production_plan.md`
   多租户与角色权限落地方案
13. `13_plan_manager_foolproof_manual.md`
   面向计划主管的端到端傻瓜式操作手册
14. `14_scheduler_foolproof_manual.md`
   面向排产员的端到端傻瓜式操作手册
15. `15_dispatcher_foolproof_manual.md`
   面向生产调度员的端到端傻瓜式操作手册
16. `16_timefold_strategy_migration_plan.md`
   面向 Timefold 策略扩展与迁移的生产落地方案
17. `17_frontend_backend_500_root_cause_and_delivery_plan.md`
   前后端 500 根因、P0 止血与认证闭环落地方案

## 写作说明

- 文章内容以当前仓库中的真实实现为准，重点参考：
  - `aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve`
  - `aps/jvs-aps-mgr/src/main/java/cn/windwheel/aps/controller`
  - `aps-ui/src/views/aps`
  - 根目录现有架构与设计文档
- 行文风格参考 `style` 目录：
  - 章节式展开
  - 先讲业务矛盾，再讲工程分层
  - 大量使用表格、流程图、代码片段和参数对比
  - 每章末尾附“关键数字”和“下章预告”

如果把这套文章看成一本书，它不是介绍“抽象的 APS 概念”，而是在解释：**当前这个 JVS-APS 系统为什么这样设计、它已经具备哪些能力、以及下一步还能怎样演进。**

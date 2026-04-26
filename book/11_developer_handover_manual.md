# 第十一章：开发接手架构与接口手册

> 适用对象：后端开发、前端开发、架构师、二开接手人员  
> 使用目标：帮助接手者快速理解系统结构、核心链路、关键接口、扩展点与当前技术债

---

## 1. 接手时先建立的认知

JVS-APS 不是普通 CRUD 系统，它的主线是：

```text
主数据准备
-> 订单拆解与 MRP
-> SchedulingSolution 组装
-> OptaPlanner 求解
-> 待确认计划
-> 甘特图预览
-> 任务调整
-> 确认计划
-> 派工 / 报工
```

如果只按模块平铺看代码，会看碎。正确方法是按这条主链理解。

---

## 2. 代码结构

### 2.1 后端模块

| 模块 | 作用 |
|------|------|
| `aps/jvs-aps-common` | 领域模型、求解器、服务实现、聚合服务、报表与调整逻辑 |
| `aps/jvs-aps-mgr` | Spring Boot 启动类、Controller、应用配置 |

### 2.2 前端模块

| 模块 | 作用 |
|------|------|
| `aps-ui/src/views/aps/dataBase` | 主数据页面 |
| `aps-ui/src/views/aps/productionEngineering` | 工艺与日历配置 |
| `aps-ui/src/views/aps/programOfProduction` | 排产策略、计划、甘特图 |

### 2.3 关键文档

建议接手顺序：

1. `book/08_production_operations_manual.md`
2. `book/03_solver_architecture.md`
3. `book/04_time_calculation_forward_backward.md`
4. `book/05_constraint_scoring_and_strategy.md`

---

## 3. 启动入口与基础配置

### 3.1 应用入口

启动类：

`aps/jvs-aps-mgr/src/main/java/cn/windwheel/aps/JvsApsApplication.java`

### 3.2 当前配置

配置文件：

`aps/jvs-aps-mgr/src/main/resources/application.yml`

当前特点：

1. 单配置文件。
2. MySQL、Redis 都写在本地默认值。
3. 无 profile 切换。

### 3.3 当前上下文实现

过滤器：

`ContextHolderFilter`

当前实现直接写死：

```java
SystemThreadLocal.set(SysConstant.TENANTID, "1");
```

这意味着：

1. 当前逻辑是伪多租户。
2. 后续做 SaaS 化时，这里是必须改造点。

---

## 4. 排产核心数据装配

### 4.1 BasicDataComponent

核心类：

`aps/jvs-aps-common/.../solve/component/BasicDataComponent.java`

作用：

1. 查询物料。
2. 查询工艺路线。
3. 查询工作日历。
4. 查询资源。
5. 查询 BOM。
6. 查询来料计划。
7. 查询待排订单。
8. 查询 pinned 任务。

最终输出：

`BasicData`

### 4.2 BasicData

核心结构：

1. `materialMap`
2. `processRouteMap`
3. `workCalendarMap`
4. `productionResources`
5. `bomMap`
6. `incomingMaterialOrderMap`
7. `productionOrders`
8. `pinnedTasks`

这是求解前的完整输入快照。

---

## 5. 求解主链

### 5.1 发起入口

Controller：

`SmartSchedulingController.generate`

Facade：

`PlanningSmartServiceImpl.generate`

### 5.2 Generate 流程

真实流程是：

1. 检查 Redis 锁。
2. 检查是否已有进行中的排产。
3. 检查是否已有待确认计划。
4. 读取策略。
5. 通过 `BasicDataComponent` 拉取基础数据。
6. 调用 `SolveService.solve(...)`。

### 5.3 SolveServiceImpl

核心类：

`aps/jvs-aps-common/.../solve/impl/SolveServiceImpl.java`

作用：

1. 写入动态权重上下文。
2. 缓存求解任务 ID。
3. 初始化进度。
4. `buildSolution(...)`
5. `solveExecute(...)`
6. 校验结果。
7. 保存待确认计划。

### 5.4 buildSolution

核心步骤：

1. 设置策略和生产订单。
2. 计算 `planTime`。
3. 填充主资源及日历。
4. 处理已锁定任务。
5. 对订单做排序、MRP、任务拆解。
6. 把锁定任务挂回主资源任务列表。

---

## 6. SchedulingSolution 与实体模型

### 6.1 SchedulingSolution

核心类：

`SchedulingSolution`

关键字段：

1. `planTime`
2. `strategy`
3. `productionOrders`
4. `mainResources`
5. `tasks`
6. `score`

### 6.2 MainProductionResource

它是 `@PlanningEntity`。

关键设计：

```java
@PlanningListVariable(valueRangeProviderRefs = "taskRanges")
private List<ProductionTask> taskList
```

说明：

1. 解空间核心不是“任务直接分配时间”，而是“任务插入哪个资源列表、插入哪个位置”。

### 6.3 ProductionTask

它也是 `@PlanningEntity`，但当前主要以任务事实 + 阴影变量承载排产结果。

关键字段：

1. `code`
2. `priority`
3. `order`
4. `material`
5. `process`
6. `frontTaskCodes`
7. `nextTaskCodes`
8. `resource`
9. `pinned`
10. `startTime`

关键点：

1. `resource` 由 `InverseRelationShadowVariable` 从资源列表反向得到。
2. `pinned` 使用 `@PlanningPin`。
3. `startTime` 由 `ApsStartTimeListener` 计算。
4. `endTime` 不直接存，依赖 `getEndTime()` 动态推导。

---

## 7. 任务拆解与 MRP

### 7.1 MRP

核心类：

`MrpUtils`

逻辑：

1. 从订单物料出发。
2. 结合库存和来料扣减。
3. 得到欠料图 `Graph<MrpMaterial>`。
4. 若关闭物料约束，则直接只保留订单本身物料节点。

### 7.2 任务拆解

核心类：

`ProductionTaskUtils`

逻辑：

1. 根据工艺路线为制造类物料生成任务。
2. 为半成品生成补充生产订单。
3. 建立前置与后置任务关系。
4. 对首道任务附加物料延迟信息。
5. 把 pinned 任务映射回新生成任务关系图。

这部分是系统非常关键的业务正确性基础。

---

## 8. 时间计算机制

### 8.1 核心监听器

`ApsStartTimeListener`

作用：

1. 当资源任务列表变更时，读取当前策略方向。
2. 调用正排或逆排时间服务。

### 8.2 正排

核心类：

`TaskTimeForwardServiceImpl`

考虑因素：

1. 资源内前序任务结束时间。
2. 前置工序约束。
3. `ES / EE` 工艺关系。
4. 前后间隔、缓冲时间。
5. 锁定任务冲突。
6. 物料延迟。
7. 工作日历。

### 8.3 逆排

核心类：

`TaskTimeBackwardServiceImpl`

考虑因素：

1. 资源后续任务开始时间。
2. 订单交期。
3. 后置工序约束。
4. `ES / EE` 对偶推导。
5. 锁定任务冲突。
6. 工作日历。

### 8.4 日历工具

核心类：

`TaskCalendarUtils`

作用：

1. 对齐到工作时间。
2. 计算跨班次结束时间。
3. 计算逆排开始时间。
4. 计算某时间区间工作时长。

---

## 9. 求解器装配

### 9.1 SolveManageConfigurator

核心类：

`SolveManageConfigurator`

当前装配内容：

1. `SchedulingSolution.class`
2. `ProductionTask.class`
3. `MainProductionResource.class`
4. `ApsHardSoftScoreConstraintProvider.class`
5. `ConstraintStreamImplType.BAVET`
6. 终止条件
7. `moveThreadCount("AUTO")`

### 9.2 当前支持的 profile

1. `DEFAULT`
2. `FFD_TABU`
3. `FFD_LATE_ACCEPTANCE`

### 9.3 终止条件

默认：

1. 总时长上限 1 小时。
2. 若 `maxNoImprovementTime` 未设置，则以可行解作为重要终止条件。

### 9.4 当前架构特征

1. `SolverManager` 以租户 ID 作为 Map key。
2. 当前由于租户写死，本质上还是单租户实例。

---

## 10. 约束体系

### 10.1 约束提供者

核心类：

`ApsHardSoftScoreConstraintProvider`

### 10.2 已定义约束

当前代码包含：

1. `arrangeByPriority`
2. `equipmentCanOnlyHandleMatchingProcesses`
3. `finishEarly`
4. `minimizeTardiness`
5. `balanceResourceLoad`
6. `minimizeGapBetweenDependentTasks`
7. `noResourceTimeOverlapping`
8. `theEquipmentCanOnlyProcessOneProcessAtTheSameTime`
9. `minimizeSetupTime`

### 10.3 动态权重

通过：

`DynamicConstraintWeightContext`

当前机制：

1. 使用 `ThreadLocal` 放置 `ConstraintWeightConfigDTO`。
2. 在 `SolveServiceImpl.solve()` 中 set。
3. 在 finally 中 clear。

### 10.4 当前注意点

1. 权重上下文是线程绑定的。
2. 若后续改造成更复杂异步或多阶段并行，需要重新核对权重传递是否稳定。

---

## 11. 待确认计划与正式计划

### 11.1 待确认计划

核心聚合服务：

`PlanSolutionFacadeServiceImpl.saveSolutionPending`

落表：

1. `jvs_plan_task_pending`
2. `jvs_plan_task_order_pending`
3. `jvs_plan_info` 中的未确认状态

### 11.2 正式确认

核心方法：

`PlanSolutionFacadeServiceImpl.saveSolution`

做的事：

1. 把 pending 任务转为正式任务。
2. 保留已有任务进度相关字段。
3. 更新订单排产状态。
4. 清除 pending。
5. 清除调整缓存。
6. 更新计划信息。

这是当前业务闭环成熟度较高的地方。

---

## 12. 报表与甘特图接口

### 12.1 预览接口

Controller：

`SmartSchedulingController`

主要接口：

1. `/smart-scheduling/preview/plan/tasks`
2. `/smart-scheduling/preview/plan/resource/gantt`
3. `/smart-scheduling/preview/plan/order/gantt`
4. `/smart-scheduling/preview/plan/material/gantt`

### 12.2 正式结果接口

Controller：

`ScheduleResultController`

主要接口：

1. `/schedule-result/report/plan/tasks`
2. `/schedule-result/report/plan/resource/gantt`
3. `/schedule-result/report/plan/order/gantt`
4. `/schedule-result/report/plan/material/gantt`
5. `/schedule-result/report/plan/task/detail/{code}`

### 12.3 报表实现

典型实现类：

1. `ResourceTaskGanttServiceImpl`
2. `OrderTaskGanttServiceImpl`
3. `MaterialRequirementGanttServiceImpl`
4. `PlanTaskReportServiceImpl`

---

## 13. 任务调整体系

### 13.1 Controller

`TaskAdjustController`

主要能力：

1. 拆分。
2. 合并。
3. 移动。
4. 锁定/解锁。
5. 撤销。
6. 检查是否已调整。

### 13.2 Handler 机制

系统通过注解 + Handler 分发：

1. `AdjustTaskSplitHandler`
2. `AdjustTaskMoveHandler`
3. `AdjustTaskFreezeHandler`
4. `AdjustTaskMergeHandler`

对应抽象类和实现位于：

`service/facade/impl/adjustment`

### 13.3 调整数据融合

核心类：

`TaskAdjustFacadeServiceImpl`

作用：

1. 统一查询正式任务与调整任务。
2. 以调整任务优先覆盖正式任务。
3. 为甘特图和详情页提供一致视图。

这是前后端“人工调整优先”的关键粘合层。

---

## 14. 派工与报工

### 14.1 派工

Controller：

`TaskAssignmentController`

Facade：

`TaskAssignmentFacadeServiceImpl`

主要逻辑：

1. 按派工时间窗口查询未完成任务。
2. 按资源分组。
3. 结合日历计算可派工时间。
4. 导出 Excel。

### 14.2 报工

Controller：

`TaskReportController`

Facade：

`TaskWorkReportFacadeServiceImpl`

主要逻辑：

1. 导入 Excel 或改单。
2. 保存报工记录。
3. 更新任务进度。
4. 更新合并任务进度。
5. 更新订单排产状态。

---

## 15. 前端结构与调用关系

### 15.1 路由入口

`aps-ui/src/views/aps/router.js`

主要页面分组：

1. `dataBase`
2. `productionEngineering`
3. `programOfProduction`

### 15.2 关键页面

| 页面 | 文件 |
|------|------|
| 物料 | `dataBase/matter/index.vue` |
| 资源 | `dataBase/resource/index.vue` |
| 订单 | `dataBase/productionOrder/index.vue` |
| BOM | `dataBase/bom/index.vue` |
| 工艺 | `productionEngineering/technology/index.vue` |
| 工艺路线 | `productionEngineering/routing/index.vue` |
| 生产日历 | `productionEngineering/productionCalendar/index.vue` |
| 排产策略 | `programOfProduction/productionSchedulingStrategy/index.vue` |
| 排产计划 | `programOfProduction/productionSchedulingPlan/index.vue` |
| 资源甘特图 | `programOfProduction/productionSchedulingPlan/resourceGantt/index.vue` |

### 15.3 关键特点

1. 前端 API 层基本按页面拆分。
2. 资源甘特图页是最复杂页面，承载预览、调整、报工、派工。
3. 策略页当前没有完整开放后端高级 profile 参数。

---

## 16. 当前技术债与改造优先级

### 16.1 P0

1. 租户上下文写死。
2. 缺少权限与审计。
3. 配置未环境化。

### 16.2 P1

1. 求解器独立 Worker 化。
2. 高级策略前端化。
3. 更完善的健康检查、监控和日志。

### 16.3 P2

1. 更细粒度行业约束。
2. 快照回放和策略对比。
3. 更高性能甘特图渲染方案。

---

## 17. 接手建议顺序

如果你要真正接手这个项目，建议按下面顺序：

1. 先本地跑通前后端和 demo 数据。
2. 读 `PlanningSmartServiceImpl -> SolveServiceImpl -> BasicDataComponent -> SolveManageConfigurator`。
3. 再读 `ProductionTaskUtils / MrpUtils / TaskTimeForwardServiceImpl / TaskTimeBackwardServiceImpl`。
4. 再读 `ApsHardSoftScoreConstraintProvider`。
5. 最后再看前端甘特图和任务调整体系。

不要一开始就从所有 Controller 平铺读起，那样很难抓住系统主骨架。

---

## 18. 一句话结论

JVS-APS 的核心价值不在某一个类，而在“业务数据建模、求解器装配、时间推导、调整覆盖、待确认计划”这几条链路能闭环运行；接手开发要优先守住这条主链，而不是先做零散页面改动。

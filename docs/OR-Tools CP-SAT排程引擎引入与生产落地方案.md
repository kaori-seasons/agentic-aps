# OR-Tools CP-SAT 排程引擎引入与生产落地方案

> 日期：2026-11-25。项目分析基线：`46a59d707990707bd17e5cc5a692a493de508594`。本文保留规划设计；本轮已接入受能力门禁保护的第三引擎，具体已实现范围、部署步骤及后续阶段以 [CP-SAT 实现与运行手册](OR-Tools%20CP-SAT实现与运行手册.md) 为准，不将规划中的远期能力视为已经交付。
>
> 调研基线：OR-Tools `v9.15`，提交 `551ad10d94835c99e5e1e684500d3db398c0e345`；核对 Maven Central 发布坐标 `com.google.ortools:ortools-java:9.15.6755`。使用通用离散制造场景与专用演示用例，不绑定具体企业。

## 1. 技术决策

建议引入 **CP-SAT 第三求解引擎**，保留 OptaPlanner、Timefold 及现有自适应组合排程。首要目标是获得另一种处理资源选择、时间窗与工序逻辑的求解能力，并提供可核对的目标界与解状态。先统一业务语义，再开展同输入对照，通过门禁后开放发布。

建议按三个阶段实施：

1. **等价模型与影子运行**：实现任务、可选资源、ES/EE、日历、停机、冻结、显式物料可用时间及已有软目标；只对通过能力检查的实例建模，不进入正式计划发布。
2. **第三引擎可控发布**：补齐物料分配与等待语义、正逆排契约、拆合单映射、独立验证、取消和事务保护，接入原有预览与人工确认。
3. **共享资源与组合优化扩展**：补充人力/工装、序列相关准备时间、数量化物料流，并评估 Timefold 提供起点、CP-SAT 局部修复的收益。

**上线判断依据是同一业务契约下的正确性、可维护性和端到端收益，不能仅用某个 Job Shop 示例的最短完工时间决定替换现有引擎。**

| 引入方式 | 判断 | 原因 |
| --- | --- | --- |
| 全面替换 Timefold | 不作为本轮目标 | 现有列表变量、时间监听器、物料预处理、动态事件和确认链已相互配合，直接替换容易改变业务规则 |
| 增加枚举并直接调用 `CpSolver.solve()` | 仅能作为技术演示 | 未解决日历、对象回填、发布校验、JNI 隔离、取消和跨实例调度 |
| 第三引擎 + 能力门禁 + 独立校验 | 推荐 | 保留已有流程，允许逐类问题验证、灰度和回退 |
| 外层 jDE 直接切换 CP-SAT 参数 | 后续专项评估 | 当前 jDE 搜索的是 Timefold 模板与参数，不能直接迁移参数意义和候选预算 |
| CP-SAT 解局部瓶颈、原引擎处理其余任务 | 可作为后续模式 | 需保留外部任务占用、跨边工序关系和物料预约，不能将局部窗口当作独立工厂 |

## 2. 官方项目核验与适用范围

### 2.1 需要准确理解的能力

OR-Tools 提供通用优化组件；CP-SAT 的调度原语可以表达 Job Shop、Flexible Job Shop 和多种 RCPSP 模型。行业规则、数据清洗和执行闭环仍由 APS 实现。官方仓库采用 Apache-2.0 许可；工程接入需保留许可并核对随包 native 依赖。[官方仓库](https://github.com/google/or-tools) · [固定版本发布](https://github.com/google/or-tools/releases/tag/v9.15)

| 能力 | 核验结论 | 本项目用途 |
| --- | --- | --- |
| 区间变量与机器互斥 | `IntervalVar` 与 `addNoOverlap` 表达时间区间和互斥 | 主设备、独占测试台、固定停机窗口 |
| 可选机器 | 每个候选资源一个 presence 变量及可选区间，再约束恰好选择一个 | 工序候选资源及不同资源加工时长 |
| 累积资源 | `addCumulative` 表达并发需求不超过容量 | 后续人力、共享工装、功率上限，不自动等同现有资源 `capacity` 字段 |
| 工序逻辑 | 线性关系、布尔约束和条件约束组合 | ES/EE、冻结、授权替代与工艺分支 |
| 排序与换型 | 排序变量或 circuit 等结构表达相邻关系 | 现有切换次数目标及后续序列相关准备时长 |
| 初始解提示 | `addHint` 提供变量建议 | 已确认计划、Timefold 可行方案和局部修复起点 |
| 求解界与状态 | 返回状态、目标值及目标界 | 区分找到可行解、证明不可行、未完成证明、模型错误 |

以上 API 已按固定版本源码核对：[CpModel](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/java/com/google/ortools/sat/CpModel.java) · [CpSolver](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/java/com/google/ortools/sat/CpSolver.java)。

需要修正几个容易引起实施误解的表述：

- `OptionalIntervalSampleSat.java` 是可选区间示例，不是完整的柔性车间生产模型；需要额外实现机器唯一选择、跨机器工序关系和目标函数。[Java 示例](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/sat/samples/OptionalIntervalSampleSat.java) · [柔性车间示例](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/examples/python/flexible_job_shop_sat.py)
- CP-SAT 能证明最优或不可行，但不保证所有中小实例都能在给定预算内完成证明。可行解和最优证明应分开展示。[求解状态](https://developers.google.com/optimization/cp/cp_solver)
- 当前参数明确包含 LNS 与多 worker 搜索设置。不能将 CP-SAT 理解为只做穷举，也不能将它当成现有 Tabu/晚期接受的同义替代。[参数定义](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/sat/sat_parameters.proto)
- “OR-Tools 不提供元启发式”不能概括整个工具集：Routing 模块包含引导局部搜索、模拟退火和禁忌搜索。这些是路由模型的策略，不是可直接接入本项目的行业排程模板。[官方路由选项](https://developers.google.com/optimization/routing/routing_options)
- Java API 与 Python 的业务原语相近，方法签名和表达式类型不同。Java 的 `newIntervalVar` 时长参数使用 `LinearArgument`，常量通常写成 `LinearExpr.constant(duration)`。

### 2.2 版本与运行环境

项目实际使用 Java 17、Spring Boot 2.7.12、Timefold 1.8.1、OptaPlanner 9.44.0.Final，以 [aps/pom.xml](../aps/pom.xml) 为准。不得沿用旧方案里的其他 Timefold 版本作为本轮依赖基线。

核对到 OR-Tools 9.15.6755 的发布 POM：Java 编译目标为 1.8，包含五种平台 native 包、`jna-platform:5.14.0`、`protobuf-java:4.33.1`。Java 17 可作为实施运行基线，JNI 加载与 Spring Boot 打包仍需实际验证，不能由字节码兼容推导部署通过。[发布 POM](https://repo.maven.apache.org/maven2/com/google/ortools/ortools-java/9.15.6755/ortools-java-9.15.6755.pom)

## 3. 当前实现盘点

### 3.1 已有排程链路

```text
PlanningSmartServiceImpl.generate
  → BasicDataComponent / MRP / 主数据与源签名
  → DynamicSchedulingService.prepare
  → SolveServiceImpl.buildSolution
  → DynamicSchedulingService.apply / 问题快照
  → SchedulingEngineRouter
  → 结果校验 / 求解审计 / 待确认计划
  → 资源、订单、物料甘特预览
  → 源数据签名复核 / 人工确认 / 发布
```

| 实现位置 | 当前作用 | 引入 CP-SAT 的影响 |
| --- | --- | --- |
| [SchedulingSolution](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/model/SchedulingSolution.java) | 同时承载两个引擎注解及评分 | 不把第三引擎的 native 对象或 proto 塞入该业务对象 |
| [MainProductionResource](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/model/MainProductionResource.java) | `taskList` 列表变量，Timefold 冻结前缀 | CP-SAT 显式决定资源和时间，完成后生成有序资源队列 |
| [ProductionTask](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/model/ProductionTask.java) | 开始时间为影子变量；`getEndTime()` 根据资源日历推导结束时间 | 不能仅 `setEndTime()` 回填；必须确认显式解与业务 getter 得到相同结束时间 |
| [SchedulingRules](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/score/SchedulingRules.java) | 两引擎共享的硬/软语义辅助函数 | 作为逐项映射及统一验证的主要依据 |
| [TimefoldConstraintProvider](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/timefold/TimefoldConstraintProvider.java) | 7 项硬约束、6 项软目标 | 每项建立 CP 表达、独立校验和反例测试 |
| [TaskTimeForwardServiceImpl](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/calculate/service/impl/TaskTimeForwardServiceImpl.java) | 日历对齐、工序间隔、队列相关物料等待、停机避让 | 包含评分器之外的业务行为，不能漏掉 |
| [TaskTimeBackwardServiceImpl](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/calculate/service/impl/TaskTimeBackwardServiceImpl.java) | 根据交期、后继和资源顺序反推时间 | 逆排不是把所有时间取负即可等价实现 |
| [SchedulingEngineRouter](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/config/SchedulingEngineRouter.java) | 普通双引擎路由；自适应分支优先 | 新引擎必须有独立分支和组合配置校验 |
| [DynamicSchedulingService](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/presolve/DynamicSchedulingService.java) | 事件、受影响范围、冻结、种子；当前明确限制 TIMEFOLD | 需拆出引擎无关事件层及引擎相关种子层 |
| [SolveAuditService](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/SolveAuditService.java) | 问题快照、源签名、结果、租户隔离；当前完成逻辑专门合并 adaptive 字段 | 新增统一引擎报告合并协议，避免覆盖 CP-SAT 元数据 |
| [SchedulingTaskMapper](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/util/SchedulingTaskMapper.java) | 将循环对象图映射为持久化 DTO | 继续作为结果落库桥接点 |

### 3.2 不能从字段存在推导能力已存在

- 主资源有 `capacity`，当前主设备约束仍是两任务不可重叠；它不代表已建立人数、能耗或工装的多容量调度模型。
- `minimizeSetupTimeWeight` 当前惩罚相邻不同物料的切换次数，没有工艺准备时间矩阵。
- `materialDelayMap` 是等待时长，`notBefore` 是绝对最早可用时刻；两者不可直接互换。
- 预测和智能补货提供需求、库存投影与采购建议，不等于排程中的可用批次预约和工序级耗料约束已完成。
- `TaskValidatorUtils` 是既有 DTO 校验工具，不覆盖全部评分规则；例如工序间隔与 EE 校验口径不如 `SchedulingRules` 完整，不能单独承担新引擎发布门禁。

## 4. 统一模型契约

新增无求解器依赖的不可变契约，建议模型版本为 `APS_SCHEDULING_V1`。`V1` 表示新契约首版，不表示复制所有历史时间监听器的实现细节。

| 对象 | 必需内容 |
| --- | --- |
| 请求头 | `tenantId`、`planId`、`attemptId`、源签名、契约版本、输入哈希、绝对截止时刻 |
| 时间轴 | 业务时区、时间原点、时间粒度、排程范围、半开区间约定 |
| Operation | 任务编码、订单关联、数量、可选资源与资源相关工时、前后继、冻结状态、物料预约、拆合单关系 |
| Resource | 资源标识、规范化工作窗口、停机窗口、能力关联；额外资源另建对象 |
| Dependency | ES/EE、前/后间隔、buffer、两端任务标识 |
| FrozenAssignment | 资源、开始、结束、状态、版本；来自确认基线 |
| MaterialReservation | 物料/批次、可用时刻、分配数量、消费任务、消耗时点、预约来源版本 |
| ObjectiveSpec | 六项旧目标的精确口径、权重、单位、舍入方式、常数偏移与目标版本 |
| SolverControls | 引擎、worker 数、时间预算、确定性预算、种子、gap 设置、提示来源、回退策略 |

原 `SolveProblemSnapshot` 可作为管理端恢复入口，但不直接作为 worker 对象图协议。标准化过程显式输出任务和关联 ID，拒绝重复编码、未知资源、缺失前置工序、循环依赖和非法数值。

### 4.1 时间与数量精度

1. 时间变量采用相对原点的非负整数 tick，通常优先使用秒。只有所有时间、间隔和计算工时都能精确表示时才选择该粒度；存在毫秒时切换毫秒或返回明确的精度错误，不允许无记录截断。
2. 业务时间当前为 `LocalDateTime`。迁移时保留现有本地时间语义，不能将 `toEpochSecond(UTC)` 的计算约定误认为真实 UTC。涉及夏令时歧义的时区需先完成明确的转换契约，否则能力检查拒绝。
3. 工时统一调用 `TaskDurationUtils` 的资源产能计算，支持单件、单位时间产量与整批处理口径。不能默认 `duration = quantity × 固定节拍`。
4. 物料以明确的计量单位和缩放因子转为整数；缩放后用 `BigInteger` 预检查容量、平方项和加权目标上界，再安全转换为 `long`。
5. 零工时任务需单独定义里程碑语义。首版拒绝未分类的零/负工时，避免 interval、排序与现有重叠判定出现不一致。

### 4.2 有限排程范围

范围由策略开始日、冻结基线、到货时刻、交期、工作日历覆盖和已验证可行计划共同确定。已有可行计划的最晚结束时刻可以帮助构造上界，但须再次验证其在本次扰动后仍可行。

不能使用官方示例里的“所有加工时长之和”直接覆盖夜间、周末、节假日和停机等待。若范围不足，返回 `HORIZON_INSUFFICIENT` 并记录范围来源；在有限范围内证明不可行，不等于证明整个生产问题永久不可行。扩展范围必须重新编译全部相关日历和约束，并受总体截止时刻控制。

## 5. 约束逐项映射

定义工序 `i` 的开始、结束为 `S_i, E_i`；候选资源集合为 `R_i`；选择变量为 `x_i,r`；资源相关纯加工时间为 `p_i,r`。区间统一为 `[S_i, E_i)`。

### 5.1 硬约束及外围业务规则

| 现有规则 | CP-SAT 表达与处理 | 发布校验 |
| --- | --- | --- |
| 每个活动任务必须分配 | 对 `R_i` 的 presence 变量 `ExactlyOne`；空候选集直接失败 | 活动任务集合完全一致、无遗漏和重复 |
| 主资源必须匹配工序 | 仅为工序授权候选资源创建变量 | 复核 `useMainResources` 与资源有效状态 |
| 同主资源不重叠 | 可选包络区间放入该资源 `NoOverlap` | 对恢复后的业务时间做区间扫描 |
| ES 前后置 | `S_j ≥ E_i + post_i + pre_j`；首道任务有前置关系时沿用 ES | 对每条真实依赖边调用统一规则 |
| EE 前后置 | 非首道 EE：`S_j ≥ S_i + pre_j + buffer_j` 且 `E_j ≥ E_i + buffer_j` | 两个条件均验证，不能只验证结束时间 |
| 显式物料最早可用 | `S_i ≥ notBefore_i` | 校验原始时间精度与物料来源 |
| 停机不可重叠 | 固定停机区间与任务包络共同参与 `NoOverlap` | 与 `SchedulingRules.unavailable` 一致 |
| 冻结任务不变 | 固定选定资源、开始、结束；作为其他任务的占用事实 | 逐字段对照确认基线，不只比较排序 |
| 资源日历 | 编译开始到结束的日历映射，详见第 6 节 | 生产工时与 getter 结束时间同时相等 |
| 资源队列上的前间隔 | 正排时间代码还会在资源上一任务之后加当前前间隔 | 统一成明确的相邻资源间隔规则，不能只实现工艺图间隔 |
| 队列相关物料等待 | `materialDelayMap` 依赖此前同资源任务是否已出现相关物料 | 见第 7 节；未统一时禁止以等价模式发布 |
| 拆分/合并任务 | 合并父任务为求解实体，子任务通过映射恢复；依赖边映射到实际执行实体 | 数量守恒、编码稳定、父子与订单关联不丢失 |
| 局部重排范围 | 受影响范围外的任务固定；所有跨范围依赖和资源占用仍进入模型 | 全量计划联合验证，不仅验证局部任务 |
| 订单/策略权限 | 管理端完成租户、状态、引用、策略版本检查 | worker 返回也再次核验租户和 plan/attempt |

`NoOverlap` 管理机器互斥，`Cumulative` 管理可共享容量，二者用途不同。不要为表示主设备容量而直接移除现有互斥约束。[官方 Job Shop](https://developers.google.com/optimization/scheduling/job_shop)

### 5.2 六项现有软目标的等价迁移

第一版沿用“硬约束全部成立 + 加权软代价最小”的口径。硬规则不能通过巨额罚分退化成可违反的软规则。

| 现有目标 | 当前实际含义 | CP-SAT 编码要求 |
| --- | --- | --- |
| 优先级 | 同资源、非冻结任务之间，较小任务优先级编号排在后面产生差值罚分 | 用资源共选与先后变量重化；区分订单原始优先级和已生成的任务排序编号；空值先规范化 |
| 尽早完成 | 同一权重组的最晚结束时间，按 epoch 秒加权 | 用 `MaxEquality`；改用相对秒时保存常数偏移，不能直接比较两引擎原始数值 |
| 逾期 | 每个 `endTask` 相对其订单交期的非负逾期整分钟 | `max(0,E_i-due)` 后按现有 `Duration.toMinutes()` 取整；多末工序仍按原规则累加 |
| 负载均衡 | 每资源纯加工整分钟之和的平方 | `load_r = Σ floor(p_i,r/60s) × x_i,r`，乘法等式构造 `load_r²`；先验证上界 |
| 工序连续性 | 当前任务开始减所有前置任务最晚结束的非负整分钟 | 先取前置最大结束，再取非负间隔及分钟商；EE 的重叠不会产生负奖励 |
| 换型 | 同资源上相邻的不同物料任务罚一次 | 用完整的相邻弧/排序模型计数，不将所有不同物料任务对都计为切换 |

实现前统一任务权重来源；第一版要求一次求解中的目标定义一致。若快照包含不一致权重实例，拒绝或先按明示规则分组，不默默合并。

对于分钟取整，可使用非负时长变量 `d` 与整数 `q` 满足 `60q ≤ d ≤ 60q+59`（秒粒度）。毫秒粒度对应乘以 1000。模型代价、独立校验代价和展示代价必须由同一目标规范生成。

**相邻弧实现**：对每台候选资源建立含虚拟节点 0 的 circuit；未选中的工序使用自环 `¬x_i,r`，选中工序形成经虚拟节点的单条路径；空资源使用虚拟节点自环。真实相邻弧 `i→j` 激活间隔约束与切换代价。需要覆盖空资源、单任务、冻结任务和末尾返回虚拟节点的测试，不能把虚拟弧计入换型。此模型最坏为 `O(Σ n_r²)`，必须计入模型规模预算。[排序示例](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/sat/samples/RankingSampleSat.java)

阶段三如引入“准时优先、再减少扰动、再换型”的词典序目标，应升级目标版本，分别报告各层最优性和预算；它不是旧权重模型的等价替换。

### 5.3 共享资源与真实换型的扩展模型

人力、工装和功率新增独立的 `SharedResource`、容量日历及任务需求量，不能从主设备产能字符串反推。任务在消耗该资源的区间上挂接 demand，使用 `Cumulative` 限制并发总量。变容量可以用最大容量 `Cmax` 和固定区间需求 `Cmax-C(t)` 表达暂不可用容量，需合并时间边界并验证非负性。

对跨休息工序，必须确定共享资源是整段占用还是只在实际工作片段消耗。例如夹具可能全程占用，人力和功率可能只在加工段消耗；后者需要工作片段区间，不能直接复用整段任务包络。人员资质通过候选人或技能容量约束建模，多技能人员必须保证同一人不会被多个技能池重复使用。

真实换型引入版本化 `setup(resource, previousFamily, nextFamily)`。相邻弧选中后，不仅有 `S_j ≥ E_i + setup_ij`，还要明确换型是否需要工作日历、独占机器、班组或工装，以及能否提前准备。需要资源占用时建立独立 setup interval 并参与相应资源约束。初始机台状态作为虚拟起点的工艺属性处理，冻结任务前后的换型不可遗漏。上述数据和语义未齐备前，仅保留已有切换次数目标。

## 6. 日历：接入中的首要工程难点

### 6.1 当前语义

`ProductionTask.getEndTime()` 会跨休息段累计生产工时。主资源冲突又按整个开始到结束包络计算，因此“休息期间不加工”不表示“这段时间机器可以插入另一订单”。停机窗口则与整个任务包络冲突。

例如工作段为 08:00–12:00、13:00–17:00，某任务 11:00 开始，需要 120 分钟加工，应于 14:00 结束。其资源占用包络为 11:00–14:00。若错误地用固定 `duration=120`，结果会变成 13:00；若把午休作为 `NoOverlap` 的固定阻塞区间，又会错误禁止此类跨午休任务。

### 6.2 推荐编译：分段仿射结束时间映射

1. 将资源日历按有限范围展开为有序、不重叠的可工作窗口，保留当前日历优先级和显式加班日选择规则。
2. 对每个候选资源及纯工时 `p_i,r`，使用累计可工作时间构造 `E = F_r(S,p)`。
3. 以开始所在工作段及完成所在工作段的变化为断点，分成若干合法开始域。在每段内满足 `E_i = S_i + offset_segment`，其中 offset 包含加工时间与跨越的休息时间。
4. 分段选择变量 `y_i,r,k` 的和等于 `x_i,r`。选中段才激活对应开始域和仿射关系。到班末恰好完工应归入允许的边界，不能强行延到下班次。
5. 可选区间使用变量包络时长 `E_i-S_i`，放入资源互斥；不再把休息段加入同一个互斥集合。
6. 对停机和冻结仍按完整包络执行互斥，保持当前规则。若后续要允许加工在故障中断后恢复，应作为另一个业务模型版本。

此方法是针对当前实现提出的编译设计。它避免按 horizon 的每一秒枚举所有起点，但仍须度量分段数量，并用独立算法逐段验证边界。日历规则密集时可能增长较快，超出限制应拒绝或转入经过审批的分解模式，不能丢弃区间。

### 6.3 边界与迁移要求

- 当前工具在没有有效覆盖日历时存在按连续工作处理的分支；“明确休息日”与“没有日历”不同。新增契约明确标识 `UNLIMITED` 或完整覆盖，生产配置不把未知覆盖自动解释为无限产能。
- 跨午夜工作段统一拆成两个自然日片段。现有 `getUseWorkTime` 的跨午夜条件需要专项验证，不能把历史分支视为可靠跨夜模板直接照搬。
- 只允许工作时间开始，允许恰好在工作段末尾结束。正好等于下一班次开始、日历有效期末尾、节假日交界均需测试。
- 有限范围之外不继续无限搜索。日历全闭、工时大于可用工时、到货晚于范围应有清晰失败原因。
- 作为提示输入的旧计划也走同一日历验证。有效冻结时间保持原值；与本次停机冲突时返回冲突，不擅自修改冻结任务。

官方调度文档包含时间窗、可选区间与多种日历建模思路，但不提供本项目 getter 语义的直接替换实现。[调度建模文档](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/sat/docs/scheduling.md)

## 7. 物料：区分可用时刻、数量分配与旧等待逻辑

### 7.1 当前事实

[InventoryUtils](../aps/jvs-aps-common/src/main/java/cn/windwheel/aps/solve/util/InventoryUtils.java) 会按在途日期扣减剩余数量，将到货加缓冲后的可用时刻与订单交期比较；该步骤本身不是逐工序的库存时间线求解。

正排时间代码又会查看当前资源此前任务的 `materialDelayMap`：已出现的物料等待不重复累加，再取本任务未出现物料中的最大等待。这是与资源排序相关的行为，不能简单写成 `S_i ≥ planStart + maxDelay`，也不能只迁移动态事件设置的 `notBefore`。

### 7.2 推荐统一方案

在管理端增加 `MaterialAvailabilityResolver`，从库存事实、在途批次、缓冲、部分到货及 MRP 分配生成版本化预约：

```text
可用事件：物料 + 地点 + 批次 + 可用时刻 + 可分配数量
预约明细：任务 + 需求数量 + 被分配批次/库存 + 分配数量
任务可用时刻：满足其开工消耗所需数量的预约事件中的最晚可用时刻
```

- 部分收货只增加实际库存，剩余在途数量同步减少，防止同一批货重复计入。
- 多任务共享物料时，预约数量总和不得超过同一库存或批次的可分配量。
- 按任务开工、阶段投入或其他时点消耗需明确配置；首版限定开工消耗。
- 未到但早于订单交期的物料，不得支持任务在到货前开工。
- 采购建议和预测作为上游计划事实；未经批准或未确认到货的建议不自动成为可用库存。
- 每次求解使用独立不可变预约快照，不修改生产库存；确认计划时验证源版本，避免跨会话重复预约。

物料预约规则应先在现有引擎回归后，再用于三引擎对照。保留旧规则版本便于既有计划回放；含旧 `materialDelayMap` 且尚未完成转换的实例返回 `MATERIAL_SEMANTICS_UNSUPPORTED`，不以忽略等待的方式开放发布。

### 7.3 何时引入数量化物料流

若要让求解器在多个订单之间动态决定用哪批物料，需要增加物料平衡模型：期初库存为正事件、到货为正事件、任务开工消耗为负事件，任意事件时点累计余额不小于零。可评估 reservoir，或按有序事件时点构造库存平衡；二者不能与可再生人力的 `Cumulative` 混用。

需要额外处理整数单位、同一时刻到货与消费的口径、批次质量状态、跨地点运输、替代料授权和重复事件标识。第一版只做确定性预约及可用时刻，不把补货金额优化一并塞入排程目标。

## 8. 正逆排、冻结与结果回填

### 8.1 正排与逆排的契约

正排以策略开始时间和物料可用时间为下界，交期一般进入软目标。现有逆排实现会将末工序交期纳入反推上界；不能仅把方向枚举传给 CP-SAT，然后沿用正排全部语义。

第一阶段开放正排影子运行；逆排通过专项门禁后开放。逆排契约要明确：

- 哪些交期是硬上界，哪些允许逾期；不因切换引擎改变订单承诺等级。
- 是否允许排到策略起点之前；如允许，必须明确历史可排范围及冻结事实。
- 逆排要求是得到任一满足交期的解，还是尽量靠近交期以减少提前在制。后者应有单独的晚开工/提前完工目标，不能用“最小完工时间”冒充。
- 在与当前引擎做同目标对照时，采用相同方向契约和目标版本；不把方向变化造成的差异归因于算法。

### 8.2 引擎输出应是事实，而不是再次触发排程

worker 返回 `taskCode → resourceId/start/end`、目标分项、原始状态与边界。管理端完成以下步骤：

1. 按任务编码恢复原问题关联，主订单、补充订单、拆合单关系使用规范化映射。
2. 为每个资源生成按时间排序的任务集合，保留明确的冻结任务事实。
3. 按资源、工时及日历重新计算结束时间，要求与 worker 返回的 `end` 完全一致。
4. 独立计算硬约束与软目标分项；结果不一致时拒绝保存待确认方案。
5. 映射到现有 `SchedulingTaskMapper` 和发布 DTO，继续走原预览、取消、确认事务。

**禁止用 `SolutionManager.update(...UPDATE_ALL)` 直接复核 CP-SAT 的显式时间解。** 当前影子监听器可能重新按列表左移/右移任务，改变原解后再评分。应新建无重排副作用的 `CanonicalScheduleValidator` 与 `BusinessObjectiveEvaluator`。若需要再调用 Timefold 优化，必须作为新的求解尝试独立记录输入和输出。

## 9. Java 接入与运行架构

### 9.1 模块划分

建议生产采用独立 Java worker 进程部署 CP-SAT，Web 管理端保留已有事务和业务权限。开发验证可以同进程运行，但不直接作为大规模生产默认形态。

```text
aps-ui
  → jvs-aps-mgr / PlanningSmartService
      → CanonicalProblemBuilder / CapabilityValidator
      → SchedulingEngineRouter
          ├─ OptaPlanner adapter
          ├─ Timefold / Adaptive adapter
          └─ OrToolsWorkerClient
                → 有界队列 / 租约 / 隔离 Java worker
                → CpSatModelCompiler → CpSolver
      → CanonicalScheduleValidator / BusinessObjectiveEvaluator
      → Audit → 待确认计划 → 源签名复核 → 人工发布
```

拟新增：

| 模块或类 | 职责 |
| --- | --- |
| `jvs-aps-scheduling-contract` | 无 Spring、ORM、Timefold、OR-Tools 依赖的 DTO、版本和错误码 |
| `jvs-aps-ortools-worker` | 固定 Java/JNI 运行镜像、模型编译、求解、健康检查 |
| `CanonicalProblemBuilder` | 将现有快照、任务图和资源事实标准化 |
| `CpSatCapabilityValidator` | 校验日历、精度、物料、目标、模型规模及方向支持范围 |
| `CalendarIntervalCompiler` | 编译工作窗口与开始/结束关系 |
| `CpSatModelCompiler` | 构建变量、硬约束、目标、提示及变量索引表 |
| `CpSatSchedulingAdapter` | 请求、取消、截止时刻与 worker 结果处理 |
| `CanonicalScheduleValidator` | 不依赖求解器状态、不修改时间的全量业务校验 |
| `BusinessObjectiveEvaluator` | 统一目标分项、单位与偏移；支持三引擎对照 |
| `CpSatResultMapper` | 将显式资源/时间映射回原对象与持久化 DTO |

当前路由返回 `SchedulingSolution`，建议逐步引入 `SolveOutcome` 包装任务结果、引擎报告和业务验证结果。兼容期可以保留旧返回类型，但只有独立校验通过后才构造兼容的 `HardSoftLongScore.of(0, -businessCost)`，不能因 CP-SAT 返回 `FEASIBLE` 就伪造 `0hard/0soft`。

### 9.2 JNI、依赖与打包

worker 固定依赖：

```xml
<dependency>
  <groupId>com.google.ortools</groupId>
  <artifactId>ortools-java</artifactId>
  <version>9.15.6755</version>
</dependency>
```

该依赖默认带入多平台 native 包。生产构建按平台保留正确 native artifact，排除其余平台，所有版本保持一致；不得仅复制 Java 主 JAR。优先使用普通 classpath 分发的独立 worker，避免先把 JNI 资源加载与现有 fat JAR 机制耦合。

- Linux x86-64、Linux aarch64、macOS arm64 分别运行真实原生库烟测；Windows 仅在计划支持时纳入发布矩阵。
- 固定基础镜像与 libc/动态库条件，不假设 Alpine/musl 可直接运行官方 Linux native 包。
- 进程初始化调用一次 `Loader.loadNativeLibraries()`，随后用极小模型检查 JNI、protobuf 和返回状态，成功后才进入 readiness。
- 提前验证 native 临时解压目录可写、可加载共享库；控制目录权限、容量与进程结束后的清理。[Loader 源码](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/java/com/google/ortools/Loader.java)
- 使用独立模块锁定 protobuf/JNA 依赖，生成依赖树与 SBOM，验证依赖收敛；不要在现有管理端全局覆盖 protobuf 版本来碰运气。
- JNI 崩溃、native OOM、`UnsatisfiedLinkError` 可能超出普通业务异常处理。独立进程故障由管理端记录失败，避免拖垮 Web 进程。

### 9.3 API 使用骨架

以下仅说明经源码核对的 Java 建模方式，不是已完成的业务实现：

```java
Loader.loadNativeLibraries();
CpModel model = new CpModel();
IntVar start = model.newIntVar(0, horizon, "start");
IntVar end = model.newIntVar(0, horizon, "end");
BoolVar selected = model.newBoolVar("selected_resource");
IntervalVar interval = model.newOptionalIntervalVar(
    start, LinearExpr.constant(processingTicks), end, selected, "operation_resource");
// 为每个候选资源构造区间；对同一工序的 selected 集合 addExactlyOne。
// 将 interval 加入对应资源的 addNoOverlap 集合。
// 有跨班次行为时，上述固定 processingTicks 必须换成第 6 节的包络时长。
String error = model.validate();
if (!error.isEmpty()) throw new IllegalArgumentException(error);
CpSolver solver = new CpSolver();
solver.getParameters().setNumWorkers(1).setRandomSeed(17)
    .setMaxTimeInSeconds(30.0);
CpSolverStatus status = solver.solve(model);
// 仅在 OPTIMAL 或 FEASIBLE 时读取变量；随后进行独立业务验证。
```

真实实现仍需任务唯一选择、冻结、目标、取消监测、结构校验和恢复流程。`newOptionalIntervalVar` 的未出现分支不约束同样的资源安排，其他附加约束也必须正确绑定 presence。

## 10. 并发、取消、状态与事务

### 10.1 预算与隔离

单实例请求包含排队、编译、求解、回传、验证和保存的总截止时刻。`max_time_in_seconds` 只从 `solve()` 开始计算，不能覆盖请求全链路。

明确设置 `num_workers`。其默认 0 会尝试使用机器核数，不能让多个租户同时默认占满所有核。准入规则使用 `活动作业的 worker 核数总和 ≤ 预留给求解的 CPU 配额`，同时限制每租户在途作业数、全局队列长度、模型变量/区间/弧数和序列化字节数。

`max_memory_in_mb` 在该版本参数注释中仍有纯 SAT 使用范围说明，不能当作 CP-SAT 可靠的硬内存隔离。生产由进程/容器限制 native RSS，并记录被系统终止的原因。[参数源码](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/sat/sat_parameters.proto)

### 10.2 作业与取消

建议新增持久化作业表 `jvs_solver_job`，包括 `tenant_id`、`plan_id`、`attempt_id`、状态、owner、lease 到期时间、fencing token、请求哈希、截止时刻、cancel 标记、结果引用、错误码和版本号；`(tenant_id, plan_id, attempt_id)` 唯一。索引覆盖状态与 lease 扫描。业务计划表仍只由管理端写入。

执行状态为：`QUEUED → BUILDING → SOLVING → VALIDATING → CANDIDATE_READY`；取消为 `CANCELLING → CANCELLED`，异常终态为 `FAILED`、`TIMED_OUT`、`REJECTED`。原审计终态与作业状态通过显式映射兼容，禁止直接扩散到所有旧接口。

取消流程：先持久化 cancel 意图，再对匹配 attempt 的活动 solver 调用 `stopSearch()`；worker 在编译阶段和开始求解前也检查取消。由于过早调用 `stopSearch()` 可能发生在内部 wrapper 创建前，求解期间需持续检查，不使用一次调用作为唯一保障。即使取消返回时已有可行解，管理端也按已取消状态阻止发布。

超过取消宽限时间，终止独立 worker 进程并回收资源；迟到的结果必须通过 fencing token 和状态比较拒绝。心跳、实例重启恢复、Redis 不可用以及管理端在保存前崩溃都需要测试。已有 planId、审计 ID、租户锁与源签名机制继续复用，不能绕过。

### 10.3 求解状态不能简化成成功/失败

| 原始状态 | 业务处理 |
| --- | --- |
| `OPTIMAL` | 读取解并验证；显示目标值、界及 gap 设置。启用非零 gap 阈值时，只能称满足该精度的终止条件 |
| `FEASIBLE` | 读取当前最好解并验证，可作为待确认方案；不显示已经证明全局最优 |
| `INFEASIBLE` | 记录本契约、范围和固定条件下的不可行证明，提供诊断入口；不生成待确认方案 |
| `UNKNOWN` | 未建立可行或不可行结论；记录时间/资源/取消原因，不标成“业务无解” |
| `MODEL_INVALID` | 编译或数据契约错误；展示错误编号，禁止作为正常求解失败静默回退 |
| 管理端 `CANCELLED` / worker 崩溃 | 无论是否有晚到的解，均不自动发布 |

该版本设置 gap 达到阈值后也可能返回 `OPTIMAL`。为区分严格整数最优证明与容差终止，审计同时记录阈值和实际 `objective/bound`，不要仅凭枚举文案宣称精确最优。去掉 epoch 常数偏移也会改变相对 gap 的解释，应固定归一化目标版本。

### 10.4 发布事务

worker 只产生候选。管理端在短事务/锁内核对当前 planId、attempt、取消状态、输入签名和独立验证结果，再保存待确认方案。人工确认时再次验证原主数据签名及冻结基线。

目前求解完成审计与候选保存存在独立事务调用，需要明确 `SOLVED_VALIDATED` 与 `CANDIDATE_SAVED` 的区别；候选保存失败不能留下“已发布”状态。实现候选保存与作业终态的事务一致性或 outbox/reconciliation，不将求解成功当作业务发布成功。

## 11. 与现有自适应求解、小模型的协作

### 11.1 第一期明确互斥

新增 `SolverEngineEnum.ORTOOLS_CP_SAT`。现有 `adaptive.enabled=true` 仍只适用于 Timefold；选择 CP-SAT 时后端拒绝该组合，前端同步提示并避免残留配置悄悄生效。

`entityTabuSize`、`lateAcceptanceSize`、`acceptedCountLimit`、`scoreCalculationCountLimit` 不是 CP-SAT 参数。切换引擎应使用独立配置对象，不把评分次数上限映射成 CP-SAT conflicts 或 deterministic time。

### 11.2 提示与局部修复

将同一标准化快照的可行旧计划映射为资源选择、开始/结束及必要顺序变量 hint。验证输入版本、任务集合、资源资格、冻结、时间范围后才加入提示。失效提示可以丢弃并记录原因；失效冻结条件必须报错，二者处理不同。

hint 不等于约束，不保证被采纳，也不恢复 Timefold 禁忌表或 CP-SAT 的搜索树。记录完整/部分提示、提示来源、是否有可验证初始方案，以及第一次发现可行解的时间。

后续组合模式按总预算分配，例如基线求解占一段、局部 CP-SAT 修复占一段、验证预留一段；各阶段独立报告，所有阶段 CPU 时间计入总成本。局部结果合回全量后重新校验，收益比较使用相同业务目标及总资源预算。

小模型继续负责事件理解、建议和结果解释。它不修改硬约束、不自动替换物料预约、不决定“无解”诊断中哪条业务规则可以删除。新增 CP-SAT 不需要强制调用小模型。

### 11.3 建议配置合同

以下为拟新增配置，不可直接复制到当前版本期待生效。`solveTimeSeconds` 在新契约中表示含排队在内的整次请求预算，排队还同时受服务端设置的单独等待上限限制；`searchTimeSeconds` 是内层搜索上限，实际值还要受剩余截止时刻及验证预留限制。

```json
{
  "solverEngine": "ORTOOLS_CP_SAT",
  "direction": "FORWARD",
  "materialConstrained": true,
  "solveTimeSeconds": 120,
  "randomSeed": 17,
  "adaptive": { "enabled": false },
  "cpSat": {
    "modelContractVersion": "APS_SCHEDULING_V1",
    "objectiveVersion": "LEGACY_WEIGHTED_V1",
    "executionMode": "SHADOW",
    "searchTimeSeconds": 90,
    "numWorkers": 1,
    "timeTickPolicy": "EXACT_AUTO",
    "relativeGapLimit": 0.0,
    "absoluteGapLimit": 0.0,
    "useBaselineHint": true,
    "fallbackPolicy": "NONE"
  }
}
```

配置规则：

- `executionMode` 默认为 `SHADOW`，生产发布需服务端白名单与模型覆盖门禁共同允许；前端字段不能自行授权。
- 搜索预算必须为正且不超过作业总预算；服务端先扣除已消耗时间和验证预留，剩余不足时不启动新一轮搜索。
- `numWorkers ≥ 1` 且不超过本租户及实例核数配额；不允许用户写 0 触发自动占满 CPU。
- CP-SAT 种子是有界整数，现有 `Long randomSeed` 超出支持范围时拒绝，不通过截断改变种子。
- gap 必须有限、非负。生产若允许容差退出，界面、审计与基线必须记录实际设置。
- `scoreCalculationCountLimit`、Timefold 无改进终止及 Tabu 参数在 CP-SAT 配置中不可悄悄生效。旧字段有实际值时要求显式迁移或返回不兼容错误；无改进终止若需支持，应单独定义其 CP-SAT 监测语义。
- 模型大小、请求字节数、日历分段数、相邻弧数和最大 horizon 由服务端配置为正上限。超过上限返回 `MODEL_TOO_LARGE`，不自动删除约束或裁掉任务。
- 工作日历与物料规则版本由快照决定，不能由普通调用方切换到较宽松规则来绕过校验。

## 12. 诊断、审计与产品交互

### 12.1 不可行诊断

先做确定性预检：候选资源为空、冻结冲突、依赖环、缺失工序、物料预约不足、范围内工时不足、日历全闭。预检错误应有任务/资源编号及可操作原因。

对 CP-SAT 证明不可行的模型，可以开启独立诊断尝试：去除优化目标，使用单 worker，将选定业务规则组挂到 assumption literal。返回的是足以造成冲突的 assumption 子集，不保证最小冲突集；未加标记的底层矛盾可能得不到可读解释。诊断预算独立受总截止时刻控制，超时返回“诊断未完成”。约束的条件激活能力按固定版本验证，优先给线性业务条件加标签，避免为诊断给所有全局约束统一包一层激活条件而损害性能。[模型及诊断响应定义](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/sat/cp_model.proto)

### 12.2 审计字段

扩展现有 `result_json`，至少记录：

```text
engine / engineVersion / modelContractVersion / compilerVersion
inputHash / sourceSignature / modelHash / attemptId / workerBuild
timeOrigin / timeTick / horizon / horizonSource
taskCount / candidateResourceCount / intervalCount / calendarSegmentCount / arcCount
parameters（有效值）/ randomSeed / numWorkers / hintSource / hintCoverage
queueMs / buildMs / solveMs / decodeMs / validateMs / persistMs / totalMs
rawStatus / terminationReason / objective / bestBound / actualGap / gapLimits
businessCostComponents / validationStatus / constraintViolations
cancelRequestedAt / stoppedAt / fallbackReason / artifactReferences
```

目标值的原生展示接口使用浮点数，极大整数可能损失展示精度；业务分项和总代价应独立使用整数/`BigInteger` 精确复算并序列化为字符串。原始响应、模型和输入快照按租户授权、保留期和大小限制管理，日志中不输出完整业务数据或凭据。

### 12.3 前端行为

在现有策略页增加引擎选项及 CP-SAT 专属配置区域：预算、并行度、精度、是否使用可行基线提示。日历和模型规模阈值默认由服务端管理，不向计划员暴露无业务含义的大量内部参数。

结果页区分“可行方案”“达到配置精度”“未找到可行方案”“约束冲突”“已取消”。同时展示准时情况、资源负载、调整任务数、冻结保护与原因。主界面继续沿用现有资源/订单/物料甘特、工序表及确认按钮。

对 CP-SAT 不支持的规则，提交前展示具体项目；后端必须再次校验。配置非法返回 400/422、会话冲突返回 409、worker 不可用返回 503 为建议接口约定，需与现有统一响应封装一致落实。

## 13. 需要修改的文件与接口

| 位置 | 具体工作 |
| --- | --- |
| `aps/pom.xml`、新增契约及 worker 模块 POM | 注册模块、固定版本、隔离 native/protobuf/JNA 依赖 |
| `entity/enums/SolverEngineEnum.java` | 增加 `ORTOOLS_CP_SAT`，保持历史默认值 |
| `entity/dto/planning/StrategyConfigDTO.java` | 增加受校验的 `cpSat` 配置，和 Timefold 参数分开 |
| `solve/config/SolveManageConfigurator.java` | 抽离公共配置校验；校验引擎、方向、自适应配置与参数组合 |
| `solve/config/SchedulingEngineRouter.java` | 增加独立 adapter、结果元数据、取消和失败策略 |
| `solve/presolve/DynamicSchedulingService.java` | 去掉硬编码引擎判断的前提是规则覆盖通过；拆分公共事件/冻结逻辑及各引擎种子应用 |
| `solve/impl/SolveServiceImpl.java` | 接入无副作用独立校验；规范候选保存和审计终态 |
| `solve/SolveAuditService.java`、`SolveProblemSnapshot.java` | 模型版本、CP-SAT 报告保存、可恢复输入与源签名 |
| `solve/util/TaskCalendarUtils.java` | 提供规范窗口展开接口与日历边界测试，不让新引擎复制私有分支 |
| `solve/util/TaskValidatorUtils.java` | 由统一校验层补齐全部规则；人工调整与第三引擎共用完整规则 |
| `solve/util/InventoryUtils.java`、`ProductionTaskUtils.java` | 物料预约与旧等待转换；确保不改变既有库存净算 |
| `service/facade/impl/PlanningSmartServiceImpl.java` | worker 请求、租户会话、取消、确认、重启恢复与版本保护 |
| `aps-ui/src/views/aps/programOfProduction/productionSchedulingStrategy/index.vue` | 引擎选择、配置回显、互斥校验、能力提示 |
| `aps/scripts/` 与后端测试目录 | 新增契约、日历、独立验证及真实 HTTP 测试；保留旧引擎回归 |

表中省略前缀的 Java 路径均位于 `aps/jvs-aps-common/src/main/java/cn/windwheel/aps/`。新增模块/类均为计划项。第一轮不要顺带升级 Spring Boot、Timefold 或改写全部 MRP，以便定位引擎接入引入的变化。

## 14. 验证与端到端验收

### 14.1 先证明建模正确，再测性能

| 专项 | 必测场景与断言 |
| --- | --- |
| 机器选择 | 两台候选机器加工时长不同；恰好选一台；无候选和停用资源拒绝 |
| 任务完整性 | 漏任务、重复任务、非法资源、合并子任务重复占用、订单映射错误全部拒绝 |
| ES/EE | 多前置 ES；前后间隔；EE 两端不等式；首任务配置 EE 的实际 ES 优先规则 |
| 日历 | 班内、跨午休、跨日、周末、加班、优先级重叠、全闭日历、覆盖不足、午夜边界 |
| 精度 | 小数产能、毫秒工时、数量缩放、长时域与目标溢出；不允许静默舍入 |
| 停机 | 停机在加工段、停机在跨班包络中、相接边界、重叠停机合并 |
| 冻结 | 已报工任务资源和时间不变；冻结与故障冲突；冻结不在列表前缀的独立时间事实 |
| 物料 | 提前、延期、部分到货；多个任务竞争同一批次；重复回执；缓冲；到货恰好等于开工 |
| 旧等待兼容 | 带 `materialDelayMap` 的实例转换前拒绝；转换后对照预约与既有三类 MRP 回归 |
| 软目标 | 六项逐项手算；权重为零；多末工序；60 秒取整边界；epoch 常数偏移；单任务无换型 |
| 逆排 | 交期上界、允许/禁止逾期、开始范围、非工作日交期、与旧方向契约对照 |
| 状态 | 可行限时退出、证明最优、容差退出、范围内不可行、UNKNOWN、模型错误 |
| 会话 | 多租户并发、重复提交、取消早到/晚到、RPC 超时、租约失效、worker 重启、迟到结果 |
| 发布 | 源数据变化后确认拒绝；取消不发布；校验失败不落待确认表；重复确认幂等 |

对 3–8 个任务的小实例写独立穷举器，使用完整日历和约束枚举可行计划；核对 CP-SAT 的可行性、最优目标和声明的界。这能发现“优化得很好但漏了一条规则”的问题，优于只断言返回 `FEASIBLE`。

### 14.2 对照实验

复用既有专用演示数据结构，增加 36、72、144、360 及更大规模梯度，同时改变每任务候选资源数、冻结比例、日历片段、EE 边数、物料竞争、停机密度和相邻弧数量。规模不能仅用任务数量代表。

每个实例比较：原普通 Timefold、现有自适应 Timefold、CP-SAT 冷启动、CP-SAT 提示启动。**所有对照使用同一标准化输入哈希、目标版本、硬约束和审批口径。** 外层组合的所有阶段算力都计入，不将 Timefold 评分次数与 CP-SAT 分支数当作等价预算。

每个配置至少运行预先固定的 5 个种子或重复批次。性能报告包括可行率、首次可行时间、总耗时 p50/p95、CPU 秒、峰值 RSS、模型构造时间、目标分项、业务延期/调整指标、最终界及取消耗时。极小样本的 p95 仅作观察，生产阈值应通过足够重复和持续负载测试确定。

复现模式固定版本、排序、参数、单 worker 与确定性预算；墙钟超时仍会影响截断位置。生产多 worker 模式不承诺任务顺序逐次完全一致，接受业务约束与指标的可重复性。

### 14.3 真实 HTTP 与页面闭环

在隔离数据库、Redis 和 worker 环境执行：

1. 从策略页保存 CP-SAT 配置，刷新后核对服务端回显与数据库有效配置。
2. 导入专用演示快照，生成计划，轮询作业与审计，核对参数确实进入 CP-SAT。
3. 分别触发缺件、停机、插单、交期变更，验证冻结集合和物料可用时间。
4. 查看资源、订单、物料甘特和工序表；截图必须来自该 auditId 对应的结果。
5. 独立读取任务、日历、预约与原始快照完成全量约束校验，再执行人工确认。
6. 注入取消、数据库写入失败、源数据变更、worker 崩溃和重复回执，验证没有错误发布。
7. 切回原引擎重复原有场景，确认现有排产、MRP、需求预测及补货审批不受影响。

截图是操作证据，不能替代任务集合、物料守恒和时间约束断言。证据目录建议 `docs/e2e/ortools-cpsat/`，保存输入、有效配置、响应、逐项断言、业务指标、构建信息和文件哈希。

## 15. 交付节奏与上线门槛

以下为两名后端/算法工程师、一名测试及部分前端投入的建议分解，实际工期受日历和物料规则确认影响。

| 阶段 | 建议时间 | 必交付成果 | 退出条件 |
| --- | --- | --- | --- |
| P0 契约与技术验证 | 1–2 周 | 固定依赖、native 多平台烟测、约束清单、时间与数量规范、能力检查、极小模型 | API/打包通过；不支持的实例明确拒绝 |
| P1 等价编译与影子对照 | 2–3 周 | 资源选择、ES/EE、日历、冻结、六项目标、纯验证器、独立小实例穷举 | 反例全部拒绝；同语义可行性与目标核对通过；仍不发布 |
| P2 事务与生产接入 | 2–3 周 | 物料预约语义、正逆排契约、拆合单、worker 生命周期、预览确认、前端配置 | 全约束及真实 HTTP 闭环通过，原有引擎/库存回归通过 |
| P3 灰度与调优 | 1–2 周 | 同输入基线、持续负载、取消/恢复、CPU/RSS 配额、回退演练 | 达到部署环境约定的可靠性与性能门槛后逐步扩大范围 |
| P4 额外资源与组合模式 | 单独立项 | 人力/工装、真实准备时长、数量化物料流、局部修复 | 数据契约完整，新增约束单独通过回归与收益评估 |

建议门槛：全部发布结果经独立校验为零硬违规；取消、过期快照、越权、重复回调场景零误发布；相同输入基线下既有功能回归通过。响应时间、可行率与业务收益按实际规模形成目标值，不能在调研阶段虚构统一的速度或改善比例。

灰度按租户与策略白名单开放，默认引擎不变。开关关闭后停止接收新 CP-SAT 作业，运行中的作业按明确政策完成或取消。切换不会撤销已确认计划。**默认不静默回退**；仅对事先允许的 worker 不可用/预算耗尽场景，且源签名、业务契约相同、有剩余总预算时，才能创建新的原引擎尝试，并记录回退来源；模型错误、物料语义不支持、冻结冲突不能靠回退掩盖。

## 16. 自我评估与反证

| 初始判断 | 反证或风险 | 采用的取舍 |
| --- | --- | --- |
| CP-SAT 更适合复杂硬约束，因此一定更快 | 灵活资源、长日历、相邻弧和细粒度时间可能让模型膨胀 | 按实例特征对照，保留原引擎与能力门禁 |
| 只要旧计划可行就能很好热启动 | 改期、缺料、停机及日历变化会使提示失效；hint 不是搜索状态 | 先验证提示，记录覆盖度，独立统计提示启动收益 |
| 新引擎只需要统一 score | 原 score 含 epoch 常数和分钟取整；硬规则还分散于时间计算 | 统一业务契约、独立校验和分项目标，明确偏移 |
| MRP 已经扣过库存，不必再管物料 | 数量分配与工序开工时间不是同一问题，队列等待还含隐式规则 | 先形成预约与可用事件，未完成转换的实例不发布 |
| 资源日历直接转成停机区间即可 | 会禁止当前允许的跨班延续工序 | 编译日历结束时间映射，停机与休息采用不同语义 |
| `OPTIMAL` 就是无条件严格最优 | gap 设置、有限范围和目标偏移影响解释 | 同时展示目标界、阈值和模型范围 |
| 单个 Java Future 足以管控超时 | JNI 取消、native 内存与进程崩溃不完全受 JVM 线程机制约束 | 独立 worker、截止时刻、租约、fencing 和进程资源限制 |
| 第三引擎接入后应默认开放所有问题 | 部分约束尚无必要的数据模型 | 分阶段开放，支持矩阵随模型版本发布 |

最终建议是把 CP-SAT 建设为可审计、可验证、可回退的第三引擎。近期最有价值的工作不是堆叠算法名称，而是统一日历、工序关系、物料预约与目标口径；这些基础完成后，才能可靠比较不同求解器，也能改善现有两条求解路径。

## 17. 调研与实施核对清单

本轮已完成：本地模型、评分、时间计算、MRP、动态事件、引擎路由、审计与确认链的代码核对；OR-Tools 发布元数据、固定提交 Java API、参数、模型 proto、示例与 Maven POM 核对。

本轮未执行第三引擎编译、JNI 烟测或端到端排产测试；本轮产物仅为这份开发方案。文中的模型设计需通过第 14 节验证，不应直接视为已完成的代码或性能证明。

实施前后持续维护以下清单：

- [ ] 当前引擎的 7 项硬约束、6 项软目标与外围时间/物料规则均有映射及测试。
- [ ] 日历、EE、冻结、精度、资源前间隔、拆合单与逆排有明确契约。
- [ ] `materialDelayMap` 已转换或被能力门禁明确拒绝。
- [ ] worker 平台、依赖、native 加载、RSS/CPU 与取消机制通过真实运行验证。
- [ ] 源快照、模型哈希、有效参数、原始状态、目标分项和界可追溯。
- [ ] 结果独立验证无时间重排副作用，且与持久化 DTO 一致。
- [ ] 真实 HTTP、页面预览、确认、取消、重启与并发保护通过。
- [ ] 同输入同契约基线、既有业务回归与回退演练完成。

补充官方核对入口：[最小 Job Shop Java 示例](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/sat/samples/MinimalJobshopSat.java) · [早完工/逾期代价示例](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/sat/samples/EarlinessTardinessCostSampleSat.java) · [Java 发布依赖模板](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/java/pom-full.xml.in) · [求解与时间限制](https://github.com/google/or-tools/blob/551ad10d94835c99e5e1e684500d3db398c0e345/ortools/sat/docs/solver.md)。

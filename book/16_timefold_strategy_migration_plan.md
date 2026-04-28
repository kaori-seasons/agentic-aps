# Timefold 策略扩展与迁移生产落地方案

编写时间：`2026-04-27`  
适用范围：当前仓库 `jvs-aps` 的智能排产求解层  
目标：在不破坏现有生产流程的前提下，为项目增加围绕 Timefold 的可落地策略能力，并给出一条能上线、能回滚、能对比、能演进的实施路线

---

## 1. 结论先行

### 1.1 最终结论

当前项目**适合引入 Timefold 1.x，但不适合直接跳到 Timefold 2.x**。

推荐路线不是“原地一把梭替换 OptaPlanner”，而是：

1. **先做求解引擎抽象层**
2. **并行保留 OptaPlanner 9.44 与新增 Timefold 1.x**
3. **先以 Timefold 1.x 的新策略模板做影子运行和基准对比**
4. **验证通过后，再按租户/策略逐步切流**
5. **把 Timefold 2.x 视为中长期平台升级事项，不纳入本轮直接上线范围**

### 1.2 为什么不是直接上 Timefold 2.x

从第一性原理看，排产系统的第一目标不是“技术栈新”，而是“结果可执行、行为可解释、回滚可控”。  
而当前项目与 Timefold 2.x 之间存在三道硬约束：

1. 当前项目是 `Spring Boot 2.7.12 + Java 17`
2. 当前求解模型使用了 `PlanningListVariable + ListVariableListener`
3. 当前代码仍依赖 `HardSoftLongScore`、`initScore()`、OptaPlanner 风格 API

而官方文档明确表明：

- Timefold `1.x` 支持从 OptaPlanner 升级，且支持 `Java 17+`
- Timefold Spring Boot Starter 只支持 `Spring Boot 3.5.x`
- Timefold `2.x` 要求 `Java 21+`
- Timefold `2.x` 移除了 variable listeners

这意味着：

- **Timefold 1.x**：当前项目可通过“核心库手工接入”方式落地
- **Timefold 2.x**：需要把 Java、Spring Boot、变量监听机制、分数类型、部分求解 API 一起升级

所以，把这两件事拆开，是唯一生产可用的做法。

---

## 2. 当前项目现状体检

## 2.1 技术栈现状

从代码现状看，当前项目的求解层并不是“薄封装”，而是深度嵌入式设计：

| 维度 | 当前现状 | 影响 |
| --- | --- | --- |
| Java 版本 | `17` | 可承接 Timefold 1.x，不可直接承接 Timefold 2.x |
| Spring Boot | `2.7.12` | 不适合直接用 Timefold Spring Boot Starter |
| 当前求解器 | `OptaPlanner 9.44.0.Final` | 已偏旧，且后续演进不如 Timefold 活跃 |
| 解模型 | `PlanningListVariable` | 与现代 Timefold 路线一致，但对 phase 配置更敏感 |
| 时间阴影变量 | `ListVariableListener` | 与 Timefold 2.x 的“移除 variable listener”存在直接冲突 |
| 分数类型 | `HardSoftLongScore` | Timefold 2.x 有变更，Timefold 1.x 问题较小 |
| 多租户求解管理 | `Map<tenantId, SolverManager>` | 适合做双引擎并存 |
| 策略存储 | `PlanningStrategyPO.config` JSON 字段 | 适合向后兼容扩展，不必先改表 |
| UI 策略页 | 只暴露部分策略参数 | 需要补 Timefold 相关高级配置入口 |

## 2.2 当前求解链路现状

当前求解链路主要集中在以下代码：

| 文件 | 作用 |
| --- | --- |
| `aps/pom.xml` | 锁定 `optaplanner.version=9.44.0.Final` |
| `SolveManageConfigurator` | 组装 `SolverConfig` 与 Phase |
| `SolveServiceImpl` | 执行排产、保存进度、取消任务 |
| `SchedulingSolution` | 规划解定义 |
| `MainProductionResource` | `@PlanningListVariable` 挂载点 |
| `ProductionTask` | 任务规划实体、阴影变量、Pinned 控制 |
| `ApsHardSoftScoreConstraintProvider` | 当前约束评分器 |
| `ApsStartTimeListener` | 按正排/逆排更新任务开始时间 |
| `StrategyConfigDTO` | 当前策略配置模型 |
| `PlanningStrategyPO` | 将策略配置持久化为 JSON |
| `productionSchedulingStrategy/index.vue` | 策略前端编辑页 |

## 2.3 现状中的关键优点

当前代码并非“一团旧逻辑”，反而已经具备 Timefold 落地所需的几个好基础：

1. **已经是代码式 `SolverConfig` 组装**
   - 不依赖重 XML，适合动态切引擎和模板
2. **已经有策略对象 `StrategyConfigDTO`**
   - 可以承载 solver engine / profile / algorithm 参数
3. **已经有多租户 `SolverManager` 管理**
   - 适合做租户级灰度
4. **已经是 Constraint Streams**
   - 不存在 `scoreDRL` 迁移包袱
5. **已经有随机种子与进度日志**
   - 适合做 A/B 比对和可复现回归

## 2.4 现状中的关键短板

### 短板一：引擎、算法模板、业务策略耦合在一起

当前 `SolverProfileEnum` 同时承担：

- 算法模板命名
- 具体 phase 选择
- 参数解释语义

这会导致：

- 很难优雅增加 Timefold 专属策略
- 很难长期并存多个引擎
- 很难做 A/B 对比

### 短板二：求解器 API 直接散落在业务代码里

例如 `SolveServiceImpl` 直接面向 `SolverManager<SchedulingSolution, UUID>` 编程。  
这会让 Timefold 接入时变成“大面积替换”，而不是“引擎实现切换”。

### 短板三：策略 UI 只暴露少量字段

当前前端策略页实际写回的 `config` 只有：

- `direction`
- `materialConstrained`
- `maxNoImprovementTime`

这意味着即使后端增加了 Timefold 新策略，没有 UI 承载，也难以真正被业务方使用。

### 短板四：没有标准化 benchmark 与回归基线

当前项目有少量求解相关测试，但还没有形成：

- 标准数据集
- 同题多引擎比对
- 分数、耗时、可行率、稳定性基线

没有这套基线，任何“换引擎变好了”的结论都不可信。

---

## 3. 官方边界与技术判断

截至 `2026-04-27`，结合 Timefold 官方文档，可得出以下关键判断：

## 3.1 Timefold 1.x 是当前项目的现实目标

官方升级说明指出，Timefold 1.x 面向 OptaPlanner 的升级主要是：

- GAV 坐标替换
- import 包名替换
- 部分 `javax`/`jakarta` 差异

并且 Timefold 1.x 仍支持 `Java 17+`。

这意味着：  
**对于当前 `Java 17 + Constraint Streams + ListVariable` 的项目，Timefold 1.x 是可控迁移目标。**

## 3.2 当前项目不能直接依赖 Timefold Spring Boot Starter

官方集成文档明确写明：

- Timefold Spring Boot Starter 只支持 `Spring Boot 3.5.x`

而当前项目是 `Spring Boot 2.7.12`。  
因此本轮方案必须采用：

- **Timefold Core 手工接入**
- **保留现有 `SolveManageConfigurator` 风格的代码式配置**

而不是上 Starter。

## 3.3 Timefold 2.x 必须视为独立平台升级项目

官方升级文档明确说明：

- Timefold 2.x 需要 `Java 21+`
- Spring Boot 集成已升级到 `Spring Boot 4.x`
- Variable listener 已移除
- `HardSoftLongScore` 等 long score 类型已并入新分数模型
- `initScore()` 等初始化分数字段语义发生变化

这与当前项目的下列实现直接冲突：

| 当前实现 | 与 2.x 的冲突点 |
| --- | --- |
| `ApsStartTimeListener` | 2.x 移除 variable listener |
| `HardSoftLongScore` | 2.x 分数模型变更 |
| `score.initScore()` 判断可行性 | 2.x 不再暴露该语义 |
| Java 17 | 2.x 要求 Java 21+ |
| Boot 2.7 | 2.x Spring 集成要求更高 |

因此：

**Timefold 2.x 不应纳入本轮“增加策略能力”的主交付目标。**

---

## 4. 目标、非目标与设计原则

## 4.1 本轮目标

1. 在当前架构中引入 **Timefold 1.x** 求解能力
2. 支持在策略层面选择 **OptaPlanner / Timefold** 或至少新增 **Timefold 专属策略模板**
3. 让同一数据集可以进行双引擎/多模板对比
4. 确保租户级灰度、策略级灰度、可回滚
5. 保持当前业务流程不变：
   - 生成
   - 预览
   - 放弃
   - 确认
   - 派工
   - 报工

## 4.2 本轮非目标

1. 不在本轮直接升级到 Timefold 2.x
2. 不在本轮把 Spring Boot 升到 3.5/4.x
3. 不在本轮重做全部约束模型
4. 不在本轮引入 Timefold Platform 商业能力
5. 不在本轮修改前端整体交互范式

## 4.3 设计原则

### 原则一：业务闭环高于引擎纯度

只要排产生成、预览、确认、报工的主闭环稳定，底层先双引擎并存是合理的。

### 原则二：先隔离，再替换

没有引擎抽象层，任何替换都会演化成“全仓替换 + 全域冒烟”，风险不可控。

### 原则三：先做影子比对，再做生产切换

优化器迁移不是 CRUD 升级。  
没有同数据集的可行率、硬约束、软分、耗时、稳定性比对，就不该切流。

### 原则四：让回滚成为默认能力

不是“出了问题再想怎么退回 OptaPlanner”，而是从一开始就设计成：

- 策略级切换
- 租户级切换
- 结果级比对
- 一键回退

---

## 5. 方案选型对比

## 5.1 方案 A：原地把 OptaPlanner 直接换成 Timefold 1.x

### 优点

- 改动概念上最简单
- 不需要长期维护双栈
- 可以较快摆脱 OptaPlanner

### 缺点

- 没有回退引擎
- 出现排产质量波动时难以快速定位
- 会把“引擎切换风险”和“策略调优风险”混在一起
- 一旦线上结果变差，业务只能接受“整体回退整个版本”

### 结论

**不推荐作为首发上线方案。**

## 5.2 方案 B：双引擎并存，按策略/租户灰度切换

### 优点

- 业务风险最小
- 支持同数据集对比
- 支持逐租户灰度
- 支持一键退回 OptaPlanner
- 支持先上 Timefold 新策略，再决定是否长期替代

### 缺点

- 需要多写一层引擎抽象
- 测试矩阵变大
- 短期内会有一段“双栈维护”成本

### 结论

**推荐方案。**

## 5.3 方案 C：直接跳 Timefold 2.x

### 优点

- 一步到位，未来包袱更少
- 可以直接贴合 Timefold 新 API 路线

### 缺点

- 要同步升级 Java、Spring Boot、变量监听机制、分数模型
- 不是“引擎增加”，而是“平台重构”
- 风险、工期、回归面远大于本轮目标

### 结论

**当前阶段不推荐。**

---

## 6. 推荐总体方案：双引擎 + 双阶段目标

## 6.1 总体架构

推荐把求解层拆为四层：

1. **业务编排层**
   - 继续由 `SolveServiceImpl` 驱动排产流程
2. **引擎抽象层**
   - 定义统一的 `PlanningSolverEngine`
3. **引擎实现层**
   - `OptaPlannerEngine`
   - `TimefoldV1Engine`
4. **策略装配层**
   - 根据策略配置生成对应 `SolverConfig`

推荐新增抽象：

```text
PlanningSolverEngine
  |- buildSolverManager(...)
  |- solve(...)
  |- terminate(...)
  |- getEngineName()
  |- supports(profile)
```

再由工厂层按策略配置选择引擎：

```text
PlanningSolverEngineFactory
  |- OPTAPLANNER_9 -> OptaPlannerEngine
  |- TIMEFOLD_1   -> TimefoldV1Engine
```

## 6.2 为什么要先抽象而不是先替换 import

如果只是简单把：

- `org.optaplanner` 改成 `ai.timefold.solver`

那么短期似乎快，但长期会失去四样东西：

1. 线上 A/B 对比能力
2. 策略级灰度能力
3. 租户级回滚能力
4. 对未来 Timefold 2.x 再升级的缓冲层

所以，引擎抽象不是“多此一举”，而是上线安全阀。

---

## 7. 策略模型如何演进

## 7.1 最终推荐的数据模型

当前 `StrategyConfigDTO` 建议最终演进为：

| 字段 | 说明 |
| --- | --- |
| `solverEngine` | `OPTAPLANNER_9` / `TIMEFOLD_1` |
| `solverProfile` | 通用搜索模板 |
| `direction` | 正排/逆排 |
| `materialConstrained` | 是否约束物料 |
| `maxNoImprovementTime` | 无改进终止时间 |
| `acceptedCountLimit` | 每步评估采纳数量 |
| `entityTabuSize` | 禁忌长度 |
| `entityTabuRatio` | 禁忌比例 |
| `lateAcceptanceSize` | 晚期接受窗口 |
| `simulatedAnnealingStartingTemperature` | 模拟退火起始温度 |
| `greatDelugeWaterLevelIncrementRatio` | 大洪水水位增量比例 |
| `finalistPodiumType` | 是否启用 Strategic Oscillation |
| `moveThreadCount` | 并行线程设置 |
| `randomSeed` | 可复现随机种子 |
| `environmentMode` | 断言/校验级别 |

## 7.2 过渡期怎么做更稳

从生产可用角度，不建议第一天就把 `solverProfile` 彻底重构掉。  
更稳的做法是两步：

### 第一步：低风险兼容扩展

保留现有 `SolverProfileEnum`，新增过渡值：

- `TF_TABU`
- `TF_LATE_ACCEPTANCE`
- `TF_SIMULATED_ANNEALING`
- `TF_GREAT_DELUGE`
- `TF_TABU_OSCILLATION`

优点：

- 不用先改历史数据
- 前后端改动较小
- 能快速让 Timefold 策略进系统

缺点：

- 引擎与策略语义仍耦合

### 第二步：完成语义拆分

在第二阶段把：

- `solverEngine`
- `solverProfile`

拆成两个独立字段。  
届时再把旧值通过后端兼容映射收敛到新模型。

### 我的判断

如果目标是“尽快上线第一批 Timefold 策略”，建议：

- **第一版先兼容扩展 enum**
- **第二版再做字段正交化重构**

这是一个典型的“先让业务跑起来，再把模型做漂亮”的工程权衡。

---

## 8. 推荐新增的 Timefold 策略族

结合当前业务和官方算法建议，推荐不是无脑增加很多模板，而是先增加 4 类最有价值的模板：

## 8.1 `TF_TABU`

适用：

- 多资源、多任务、存在局部最优卡住风险
- 当前 `FFD_TABU` 是业务已熟悉路径

配置建议：

- `entityTabuSize=7`
- `acceptedCountLimit=1000`

定位：

- 作为 Timefold 版本的主力基线模板

## 8.2 `TF_LATE_ACCEPTANCE`

适用：

- 需要较低每步开销
- 想比纯 tabu 更轻量

配置建议：

- `lateAcceptanceSize=400`
- `acceptedCountLimit=1`

定位：

- 作为中等规模数据集的快速策略

## 8.3 `TF_SIMULATED_ANNEALING`

适用：

- 希望快速跳出局部最优
- 求解时长较紧，要求较快给出“还不错”的结果

配置建议：

- `acceptedCountLimit=1~4`
- `simulatedAnnealingStartingTemperature` 通过基准集调优

定位：

- 作为短时求解策略候选

## 8.4 `TF_GREAT_DELUGE`

适用：

- 希望比退火更确定性
- 需要低 accepted count 的快步进

配置建议：

- `acceptedCountLimit=1`
- `greatDelugeWaterLevelIncrementRatio` 从官方建议附近开始 benchmark

定位：

- 作为稳定型快速策略候选

## 8.5 暂不建议首批纳入的模板

| 模板 | 为什么暂缓 |
| --- | --- |
| 纯 Hill Climbing | 官方也不建议在存在局部最优的真实问题中直接使用 |
| 过多组合型 acceptor | 配置空间过大，业务不可解释 |
| Timefold 2.x Neighborhoods 定制策略 | 当前项目不具备平台条件 |

---

## 9. 开发落地分阶段计划

## 9.1 Phase 0：基线建立与数据固化

工期建议：`3~5` 个工作日

### 目标

在改一行引擎代码之前，先建立客观对比基线。

### 任务

1. 固化 3 组代表性数据集
   - 小规模：几十单、少量资源
   - 中规模：典型日排产
   - 大规模：高峰期/压力数据
2. 固化输出指标
   - 是否找到可行解
   - `hardScore`
   - `softScore`
   - 总耗时
   - 任务数
   - 未排订单数
   - 逾期分钟数
   - 资源冲突数
3. 固化随机种子
4. 记录当前 OptaPlanner 结果作为基准

### 交付物

- `benchmark-dataset/` 基准输入数据
- `baseline-report.md`
- 当前 OptaPlanner 分数与耗时基线

### 验收标准

- 同一数据集可重复运行得到一致结果
- 能输出可比较的结构化结果

## 9.2 Phase 1：引擎抽象层落地

工期建议：`4~6` 个工作日

### 目标

让业务排产链路不再直接依赖某一个 solver 实现。

### 任务

1. 新增 `PlanningSolverEngine` 接口
2. 新增 `PlanningSolverEngineFactory`
3. 将 `SolveServiceImpl` 改为依赖引擎抽象
4. 把现有 OptaPlanner 逻辑下沉到 `OptaPlannerEngine`
5. 保证原行为不变

### 影响文件

- `SolveServiceImpl`
- `SolveManageConfigurator`
- 新增 `solve/engine/*`

### 验收标准

- 不接入 Timefold 时，现网行为与当前完全一致
- 现有测试全部通过
- 基准集结果与 Phase 0 一致

## 9.3 Phase 2：接入 Timefold 1.x Core

工期建议：`4~7` 个工作日

### 目标

在不引入 Spring Boot Starter 的前提下，接入 Timefold 1.x。

### 任务

1. 新增 `timefold-solver-*` 依赖
2. 保留现有 OptaPlanner 依赖，形成双栈
3. 新增 `TimefoldV1Engine`
4. 建立 Timefold 版 `SolverConfig` 组装器
5. 先只实现最小模板：
   - `TF_TABU`
   - `TF_LATE_ACCEPTANCE`

### 关键技术策略

- **不使用 Starter**
- **继续使用代码式 `SolverConfig`**
- **不先改业务流程**

### 验收标准

- 基准数据集能通过 Timefold 正常求解
- 不出现求解初始化异常
- 能输出与 OptaPlanner 同格式结果

## 9.4 Phase 3：扩展策略配置模型与前端策略页

工期建议：`4~6` 个工作日

### 目标

让计划主管可以在系统中真正选到 Timefold 策略，而不是只能写死在代码里。

### 任务

1. 扩展 `StrategyConfigDTO`
2. 扩展 `SolverProfileEnum` 或新增 `solverEngine`
3. 后端保存/查询策略时保持向后兼容
4. 前端策略页增加：
   - 引擎类型
   - 求解策略模板
   - 高级参数折叠区
5. 旧策略自动按默认值兼容

### 为什么此阶段可以不改数据库表

因为 `PlanningStrategyPO.config` 本身就是 JSON 字段，新增字段只需要：

- DTO 扩展
- 默认值处理
- 历史值兼容

通常不必先做 DDL。

### 验收标准

- 老策略不报错
- 新策略可保存、可查询、可用于排产
- UI 上能清晰区分 OptaPlanner / Timefold 策略

## 9.5 Phase 4：影子运行与基准对比

工期建议：`5~8` 个工作日

### 目标

不直接让 Timefold 结果进正式计划，而是先做平行比对。

### 任务

1. 增加影子运行开关
2. 同一输入数据：
   - OptaPlanner 跑正式结果
   - Timefold 跑对比结果
3. 保存对比指标：
   - 可行率
   - hard score
   - soft score
   - 求解时长
   - 逾期
   - 资源冲突
4. 输出对比报告

### 验收标准

- 能批量生成对比报告
- 至少 10 组真实样本完成对比
- Timefold 没有出现系统性 hard violation 回归

## 9.6 Phase 5：灰度上线

工期建议：`3~5` 个工作日

### 目标

按租户、按策略、按场景逐步切流。

### 任务

1. 先只开放给内部测试租户
2. 再开放给单个低风险客户
3. 仅允许计划主管可见 Timefold 策略
4. 预设回退开关：
   - 按租户回退
   - 按策略回退
   - 按配置全局回退

### 验收标准

- 线上切换不改代码
- 出现异常可 5 分钟内退回 OptaPlanner

## 9.7 Phase 6：中长期 Timefold 2.x 平台升级立项

工期建议：单独立项，不纳入本轮

### 前置条件

1. Java 升到 `21`
2. Spring Boot 升级到兼容版本
3. `ListVariableListener` 迁移为 Timefold 2.x 可接受机制
4. 分数类型与可行性判断逻辑重构

### 结论

这是下一阶段平台演进，不应与本轮策略引入混做。

---

## 10. 代码改造清单

## 10.1 后端

### 必改

| 文件/区域 | 改造内容 |
| --- | --- |
| `aps/pom.xml` | 新增 Timefold 1.x 依赖，保留 OptaPlanner |
| `StrategyConfigDTO` | 扩展引擎/模板/参数字段 |
| `SolverProfileEnum` | 增加过渡枚举或拆分模型 |
| `SolveServiceImpl` | 切到引擎抽象 |
| `SolveManageConfigurator` | 拆为 engine-specific config builder |
| `PlanningStrategyPO` | 无需改表，但要处理 JSON 兼容 |
| `PlanningStrategyServiceImpl` | 处理默认值与兼容映射 |
| `SolveProgressService` 相关链路 | 补充 engine/profile/version 日志 |

### 建议新增

| 新类 | 作用 |
| --- | --- |
| `PlanningSolverEngine` | 引擎 SPI |
| `OptaPlannerEngine` | 老引擎实现 |
| `TimefoldV1Engine` | 新引擎实现 |
| `PlanningSolverEngineFactory` | 按策略选择引擎 |
| `SolverProfileAssembler` | 模板到 phase config 的统一映射 |
| `SolverBenchmarkService` | 基准测试与对比报告 |

## 10.2 前端

| 页面 | 改造内容 |
| --- | --- |
| `productionSchedulingStrategy/index.vue` | 增加 Timefold 策略配置项 |
| 策略列表 | 展示引擎类型、求解模板 |
| 高级参数表单 | 根据模板动态显隐字段 |

## 10.3 测试

### 单元测试

1. 引擎工厂路由测试
2. 配置映射测试
3. 策略 JSON 反序列化兼容测试
4. 各 solver profile 的建 solver smoke test

### 集成测试

1. 同数据集双引擎结果结构一致性
2. 取消任务、租户隔离、进度日志一致性
3. 历史策略数据加载兼容性

### 基准测试

1. 小/中/大三类标准数据集
2. 固定 seed 跑 10 次分布
3. 输出 HTML/CSV 或 Markdown 报告

---

## 11. 验收指标

## 11.1 功能验收

1. 策略页能新增并保存 Timefold 策略
2. Timefold 策略可生成排产
3. 可正常预览、放弃、确认
4. 派工、报工、人工调整闭环不受影响

## 11.2 质量验收

1. 基准数据集上，Timefold 结果不得出现系统性 hard violation 回归
2. 小中规模数据集求解时长不得显著劣化
3. 老策略完全兼容
4. 任一租户可在配置层一键回退到 OptaPlanner

## 11.3 建议的量化红线

| 指标 | 红线 |
| --- | --- |
| hard violation 数 | 不得高于当前 OptaPlanner 基线 |
| 结果缺任务/缺订单 | 0 |
| 线上回滚时间 | 5 分钟内 |
| 新引擎发布后 P1 事故 | 0 |
| 基准数据集回归覆盖 | 至少 10 组真实数据 |

---

## 12. 风险与应对

## 12.1 风险一：Timefold 接入后“能跑，但结果变差”

### 本质

优化器迁移最常见的失败不是异常，而是结果质量悄悄下降。

### 应对

1. 先做影子运行
2. 固化 benchmark
3. 用固定 seed 比对
4. 以 hard feasibility 为第一门槛

## 12.2 风险二：历史策略 JSON 无法兼容

### 本质

旧策略没有新字段，可能出现空值或错误映射。

### 应对

1. 所有新字段后端设默认值
2. 保持旧 enum 值继续可读
3. 加一轮数据回填脚本，但不强依赖回填才能运行

## 12.3 风险三：双栈维护成本上升

### 本质

短期内会有 OptaPlanner 和 Timefold 两套 builder。

### 应对

1. 把共性逻辑下沉到 profile assembler
2. 只保留必要模板，不泛滥扩展
3. 等 Timefold 1.x 跑稳后再决定是否淘汰 OptaPlanner

## 12.4 风险四：误把 Timefold 2.x 升级混入本轮

### 本质

一旦把 Java 21、Boot 4、variable listener 重构、score 迁移绑在一起，项目会失控。

### 应对

明确立项边界：

- 本轮：Timefold 1.x + 双引擎策略
- 下轮：Timefold 2.x 平台升级

---

## 13. 自我辩证与反思

## 13.1 为什么不建议“只增加几个 Timefold 策略枚举就完事”

这当然最快，但它只解决了“配置能选”，没有解决：

- 引擎隔离
- 回滚
- 对比
- 长期演进

这样做的结果通常是：  
短期上线看似快，后续每增加一个策略模板，代码耦合再加一层。

## 13.2 为什么我仍然允许“第一版先扩充 enum”

因为工程上要承认现实：

- 你们当前 `config` 是 JSON
- 当前 UI 承载能力有限
- 当前业务已经理解 `solverProfile`

所以第一版完全可以接受“模型不够优雅，但足够稳定”的中间态。  
关键不是第一版长得漂不漂亮，而是第二版是否留好了拆分口。

## 13.3 为什么不建议现在就追求 Timefold 2.x

因为这会把“策略扩展项目”变成“平台升级项目”。  
两者的业务收益、测试范围、上线节奏完全不是一个量级。

在排产系统里，最危险的不是旧，而是：

- 一次改太多
- 没有基准
- 没有回退

---

## 14. 推荐排期

在 1 名后端主力、1 名前端配合、1 名测试/业务验证支持的前提下，建议排期如下：

| 阶段 | 工期 |
| --- | --- |
| Phase 0 基线建立 | 3~5 天 |
| Phase 1 引擎抽象 | 4~6 天 |
| Phase 2 接入 Timefold 1.x | 4~7 天 |
| Phase 3 策略模型与 UI | 4~6 天 |
| Phase 4 影子运行与 benchmark | 5~8 天 |
| Phase 5 灰度上线 | 3~5 天 |

总计建议：`23~37` 个工作日

如果团队希望更保守，可把 Phase 4 拉长到两周，优先换取结果可信度。

---

## 15. 最终建议

如果你问我“现在应不应该围绕 Timefold 增加策略”，答案是：

**应该。**

但如果你问我“应不应该现在就把 OptaPlanner 整体推倒重来、直接跳到 Timefold 2.x”，答案是：

**不应该。**

当前项目最优解不是激进替换，而是：

1. 先用 **Timefold 1.x Core** 在现有 `Java 17 + Boot 2.7` 体系内安全接入
2. 以 **双引擎并存** 方式获得 A/B 对比、灰度、回滚能力
3. 先上少量高价值 Timefold 策略模板
4. 用 benchmark 和影子运行说服业务，而不是用“新框架更先进”说服业务
5. 把 Timefold 2.x 作为下一阶段平台升级事项独立处理

这条路线最慢的地方不是写代码，而是建立可信基线。  
但这恰恰是排产系统这种高业务影响场景里，最不能省的部分。

---

## 16. 参考资料

以下官方资料已于 `2026-04-27` 查证：

1. Timefold Solver 1.x Upgrade from OptaPlanner  
   `https://docs.timefold.ai/timefold-solver/1.x/upgrading-timefold-solver/upgrade-from-optaplanner`

2. Timefold Solver Integration  
   `https://docs.timefold.ai/timefold-solver/latest/integration/integration`

3. Timefold Solver Local Search Algorithms  
   `https://docs.timefold.ai/timefold-solver/latest/optimization-algorithms/local-search`

4. Timefold Solver Benchmarking and Tweaking  
   `https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/benchmarking-and-tweaking`

5. Upgrade from Timefold Solver 1.x to 2.0.0  
   `https://docs.timefold.ai/timefold-solver/latest/upgrading-timefold-solver/upgrade-from-v1`


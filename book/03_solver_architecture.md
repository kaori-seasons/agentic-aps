# 第三章：求解器架构与策略模板——从配置到一次完整求解

## 总论：同样一批任务，为什么排出来的结果会不同？

在 APS 系统里，数据只是起点，真正决定结果质量的，是求解器的装配方式。

同一批任务，如果使用不同的搜索阶段、终止条件、权重模板，可能出现完全不同的结果：

```
同一批订单
    ↓
不同求解策略
    ↓
不同搜索路径
    ↓
不同的排产结果
```

JVS-APS 当前版本已经把“求解器如何装配”做成了明确的策略体系，而不是单纯依赖 OptaPlanner 的默认黑盒行为。

---

## 一、求解入口：`SolveServiceImpl`

### 1.1 排产执行的主流程

`SolveServiceImpl.solve(...)` 大体可以概括为：

1. 载入策略动态权重
2. 写入当前排产任务 ID 到 Redis
3. 初始化排产进度
4. 构造 `SchedulingSolution`
5. 调用 `solveExecute(...)`
6. 校验结果
7. 保存到待确认计划
8. 更新进度状态

这条链路说明当前系统不是“同步接口直接返回最终结果”，而是一个 **可跟踪、可取消、可确认** 的求解过程。

### 1.2 为什么要有“待确认计划”

当前系统在求解完成后，并不是立即覆盖正式计划，而是先进入 pending 状态：

- 可以预览
- 可以放弃
- 可以确认

这是一种非常成熟的生产系统设计，因为排产不是普通查询，它会直接影响现场执行。

---

## 二、`SolveManageConfigurator`：真正决定求解行为的地方

### 2.1 当前求解器装配框架

`SolveManageConfigurator` 做了三件关键事情：

```java
SolverConfig solverConfig = new SolverConfig()
    .withSolutionClass(SchedulingSolution.class)
    .withEntityClasses(ProductionTask.class, MainProductionResource.class)
    .withConstraintProviderClass(ApsHardSoftScoreConstraintProvider.class)
    .withConstraintStreamImplType(ConstraintStreamImplType.BAVET)
    .withTerminationConfig(terminationConfig)
    .withMoveThreadCount("AUTO");
```

这意味着当前系统已经明确绑定了：

- 规划解类
- 规划实体类
- 约束评分器
- Constraint Streams 实现
- 终止条件
- 自动并行线程数

### 2.2 多租户求解器管理

类中使用：

```java
Map<String, SolverManager<SchedulingSolution, UUID>>
```

以租户 ID 作为 key 维护求解器实例。这个设计的意义是：

- 避免不同租户的排产任务互相污染
- 每次切换策略时可重新创建对应 `SolverManager`
- 在取消排产时能精准终止当前租户的求解任务

---

## 三、当前系统内置的三类策略模板

### 3.1 模板一：`DEFAULT`

| 模板 | 含义 | 特点 |
|------|------|------|
| `DEFAULT` | 依赖 OptaPlanner 默认阶段配置 | 简单、兼容、适合作为回退策略 |

它的价值不在“最强”，而在于：

- 兼容老策略
- 作为策略解析失败时的保底方案
- 方便和显式模板做 A/B 对比

### 3.2 模板二：`FFD_TABU`

这是当前系统最有代表性的显式模板：

```java
FIRST_FIT_DECREASING + TABU_SEARCH
```

其思想是：

1. **FFD** 先给出一个可行的初始排法
2. **Tabu Search** 在局部搜索中避免反复走回头路

默认参数来自 `StrategyConfigDTO`：

| 参数 | 默认值 | 作用 |
|------|-------|------|
| `entityTabuSize` | 7 | 禁忌表长度 |
| `acceptedCountLimit` | 1000 | 每轮邻域评估数量 |

### 3.3 模板三：`FFD_LATE_ACCEPTANCE`

这是另一种显式局部搜索策略：

```java
FIRST_FIT_DECREASING + LATE_ACCEPTANCE
```

默认参数：

| 参数 | 默认值 | 作用 |
|------|-------|------|
| `lateAcceptanceSize` | 400 | 晚期接受缓存长度 |
| `acceptedCountLimit` | 1000 | 每轮评估邻域数量 |

这个模板的意义在于：当禁忌搜索对某些数据集不够稳定时，可以使用另一种“允许暂时接受较差解”的跳出局部最优方式。

---

## 四、策略配置项不只是“开关”

### 4.1 `StrategyConfigDTO` 的核心字段

当前系统已经把排产策略抽成结构化配置：

| 字段 | 作用 |
|------|------|
| `direction` | 正排 / 逆排 |
| `materialConstrained` | 是否约束物料齐套 |
| `maxNoImprovementTime` | 多久无改进则终止 |
| `solverProfile` | 求解器模板 |
| `entityTabuSize` | 禁忌长度 |
| `lateAcceptanceSize` | 晚期接受窗口 |
| `acceptedCountLimit` | 邻域评估规模 |
| `constraintWeightConfig` | 约束权重 |
| `randomSeed` | 结果可复现的随机种子 |

### 4.2 为什么 `randomSeed` 很重要

排产系统在生产环境中经常面临一个问题：

> 同样的数据，为什么昨天排出的结果和今天不一样？

引入固定 `randomSeed` 后，至少可以在实施和调试阶段获得更强的可复现性，这对：

- 问题定位
- 客户验收
- 策略对比

都非常关键。

---

## 五、终止条件的现实意义

### 5.1 当前默认终止逻辑

`SolveManageConfigurator` 中默认配置：

- 总求解时间上限：**1 小时**
- 终止组合方式：`OR`
- 如果没有配置 `maxNoImprovementTime`，则以“找到可行解”为重要终止信号

这说明当前系统的出发点不是“无限追求理论最优”，而是：

**在业务可接受的时间里，尽快得到一份可执行的解。**

### 5.2 为什么这很符合 APS 场景

排产系统面对的是业务时效，不是算法竞赛。

如果一套算法 3 小时后才能给出更优 2% 的结果，而车间 10 分钟后就要开工，这个“更优解”在业务上没有意义。

---

## 六、订单排序：求解前的第一层偏好注入

当前系统在生成任务前，先按规则排序订单：

```java
priority DESC
+ deliveryTime ASC
```

若前端没有自定义规则，则使用默认排序。

这很关键，因为它意味着系统在进入求解器之前，已经先做了一层业务优先级编码：

- 优先级高的订单先被处理
- 交期近的订单更早进入任务生成流程

这并不能替代求解器，但能显著影响初始任务集的形态。

---

## 七、本章总结

### 核心洞察

1. **JVS-APS 的求解是可配置的，不是单一路径。**
2. **当前系统已经显式支持三类求解模板。**
3. **策略不仅控制算法，也控制方向、终止、复现和业务偏好。**
4. **求解完成后先进入“待确认计划”，这是生产系统而非实验脚本的典型特征。**

### 关键数字

- 内置策略模板：3 个
- 默认总求解时长上限：1 小时
- `FFD_TABU` 默认禁忌长度：7
- `FFD_LATE_ACCEPTANCE` 默认缓存长度：400
- 默认局部搜索评估步数：1000

### 下章预告

第四章将进入排产引擎最核心的计算层：**正排、逆排、影子变量和时间监听器**。也就是任务被放进资源列表后，系统到底是怎样把“顺序”变成“具体开始时间”的。

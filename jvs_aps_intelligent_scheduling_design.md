# JVS-APS 智能排产算法 (FFD + Tabu) 技术设计方案

> **Document Type**: Technical Design Document
>
> **Status**: Draft
>
> **Related Code**: `aps/jvs-aps-common/src/main/java/cn/bctools/aps/solve/config/SolveManageConfigurator.java`, `aps/jvs-aps-common/src/main/java/cn/bctools/aps/solve/score/`

---

## 1. 背景与动机 (Background and Motivation)

### 1.1 问题陈述 (Problem Statement)

当前系统默认的排产机制在应对复杂现实情况时存在显著局限。许多制造企业（如电子元器件、轮胎等行业）在生产排程中仍高度依赖“人工经验 + Excel”。计划员经验丰富时，能在理想状态下维持生产平衡，但现实生产环境充满变数：

1. **订单临时插单**：紧急订单频繁打乱原有计划。
2. **交期频繁调整**：客户需求变更导致原有排期失效。
3. **设备突发故障**：产能瞬间下降，需要紧急重新分配任务。
4. **模具切换成本高**：不同产品之间的切换（Setup Time）耗时且昂贵，顺序不当导致巨大产能浪费。

**核心矛盾**：当人员变动或订单波动加剧时，排产质量明显下降。这**并非单纯的产能不足，而是资源利用不均衡以及应对动态变化的响应能力弱**。仅凭人工或简单的规则难以在多维度约束（交期、换型成本、设备利用率等）之间找到全局最优解。

### 1.2 量化影响 (Quantified Impact)

| 场景 | 人工/简单规则排产痛点 | 智能排产 (FFD+Tabu) 预期效果 |
|---|---|---|
| **多订单混排** | 容易导致工序间堆料（WIP高）或交期延误 | 全局视角优化，在交期和设备利用率间寻找最优平衡点 |
| **突发插单/故障** | 全盘推翻重排，耗时以小时计，极易出错 | 在约束范围内局部重排（Rescheduling），分钟级响应 |
| **换型频繁 (如电子元器件)** | 难以手工计算复杂的换型矩阵，导致切换时间占比过高 | 算法自动聚集同属性订单，最小化切换成本 (Setup Time) |
| **工序强耦合 (如轮胎制造)** | 硫化时间要求严苛，同时需要设备与模具资源，人工难以对齐 | 算法通过硬约束严格保证多重资源约束与工序时间依赖 |

### 1.3 设计目标 (Design Goals)

本提案为 JVS-APS 设计一个显式、可控且针对多行业优化的智能排产引擎，解决以下问题：

1. **显式算法编排**：弃用黑盒默认策略，明确组合 `Construction Heuristic (FFD)` + `Local Search (Tabu)`。
2. **行业适配性配置**：提供针对不同行业（如电子元器件、轮胎等）的约束与评分权重模型。
3. **支持动态重排**：通过锁定（Pinning）机制支持临时插单等局部重排场景。
4. **参数化调优**：禁忌表长度、评估步数等核心参数可配置，支持策略回滚与 A/B 测试。

### 1.4 非目标 (Scope Out)

- **替换底层引擎**：继续使用 OptaPlanner 作为核心求解器，不引入 Gurobi 等商业求解器。
- **完全自动化决策**：算法提供高质量基准解和辅助决策，最终执行仍允许计划员进行微调。

---

## 2. 约束 (Constraints)

### 2.1 架构约束 (Architectural Constraints)

**C-1: 必须兼容现有的 OptaPlanner 求解器模型**
当前模型采用 Planning List Variable（`MainProductionResource.taskList`）和 Shadow Variable 计算时间。算法必须基于 List Variable 模型选择相应的 Move Selector（例如 List Change / List Swap）。

**C-2: 不能破坏现有业务逻辑的数据结构**
所有的排产业务输入（订单、设备、工序）数据结构不变。约束权重的扩展必须在配置层完成，对核心实体低侵入。

### 2.2 兼容性约束 (Compatibility Constraints)

**C-3: 向后兼容 — 默认回滚机制**
当没有配置新策略或新策略配置解析失败时，必须能够平滑回退到现有的系统默认排产策略（未指定 Phase 时的默认行为）。

### 2.3 性能约束 (Performance Constraints)

**C-4: 评分计算的增量化要求**
排产问题属于 NP-Hard 问题，Tabu Search 每秒可能进行数千次评估。必须使用 ConstraintStreams API，并确保所有软硬约束都支持增量计算（Incremental Score Calculation），避免全量遍历导致的 OOM 或极慢的求解速度。

---

## 3. 当前状态分析 (Current State Analysis)

### 3.1 现有排产策略清单

目前系统在 `SolveManageConfigurator.java` 中仅仅设置了：
- `solution/entity` 模型
- `constraintProviderClass`
- `termination` 终止条件（`minutesSpentLimit`, `unimprovedSecondsSpentLimit`）
- `moveThreadCount`

**关键发现**：完全未配置任何 Construction Heuristic (CH) 和 Local Search (LS) 的具体实现。

### 3.2 现有系统缺失的核心能力

1. **策略不可控**：OptaPlanner 的默认策略对业务而言是黑盒，随着版本升级可能改变其行为，难以针对特定场景（如换型成本极高）进行定向优化。
2. **缺乏跳出局部最优的保障**：仅凭默认策略容易陷入局部最优解，尤其是在轮胎制造等硬约束极多的场景。
3. **约束不可配置**：目前不同行业的偏好（如电子行业看重换线时间，机加工行业看重设备利用率）无法通过一套系统灵活配置。

---

## 4. 设计 (Design)

### 4.1 整体架构 (Overall Architecture)

```mermaid
graph TB
    subgraph 配置阶段 (Compile/Config Time)
        A[租户/行业配置] --> B[StrategyConfigDTO]
        B --> C[解析生成 SolverConfig]
        C --> D[加载 Constraint Weights]
    end
    subgraph 求解运行阶段 (Runtime Phase)
        E[未排程任务/锁定任务] --> F[SolverManager 启动]
        F --> G[Phase 1: FFD (First Fit Decreasing)]
        G --> H[Initial Solution]
        H --> I[Phase 2: Tabu Search]
        I --> J[Best Solution 迭代]
        J --> K[满足终止条件]
    end
    subgraph 反馈与监控 (Feedback)
        K --> L[落库与指标收集]
        L --> M[A/B 对比大盘]
    end
```

### 4.2 行业特征与多目标建模 (Industry Modeling)

在 `ConstraintProvider` 中抽离约束逻辑，并依赖于动态的权重配置表：

| 行业场景 | 约束类型 | 特性描述 | 权重示例 (Weight) |
|----------|----------|----------|-------------------|
| **电子元器件** | 最小化换型时间 | 同一设备连续生产相同物料/板厚的任务免受惩罚 | `Soft: 1000` (高优先级) |
| **电子元器件** | 逾期惩罚 | 延迟交付的任务按天数/小时数叠加惩罚 | `Soft: 500` |
| **轮胎制造** | 多资源同步 | 任务需要设备可用，同时指定模具也必须空闲 | `Hard` (必须满足) |
| **轮胎制造** | 工序间时间约束 | 密炼到成型的等待时间有严格上下限 | `Hard` (必须满足) |

### 4.3 构造启发式设计：FFD (First Fit Decreasing)

**目标**：快速找到一个质量可接受且大概率不会破坏硬约束的初始解。

**策略实现**：
1. 要求 `TaskEntity`（或相关排产实体）实现 `difficultyComparator`。
2. 排序规则：优先处理交期最紧、加工时间最长、需要最稀缺资源（如特种模具）的任务。
3. Phase 注入代码：
```java
ConstructionHeuristicPhaseConfig chPhaseConfig = new ConstructionHeuristicPhaseConfig()
    .withConstructionHeuristicType(ConstructionHeuristicType.FIRST_FIT_DECREASING);
```

### 4.4 局部搜索设计：Tabu Search

**目标**：在初始解的基础上，通过系统性探索跳出局部最优陷阱，逼近全局最优解。

**核心参数设计**：
- `entityTabuSize`：默认值为 `7`（基于经验）。此值决定了被移动的实体（如订单任务）在多少个 step 内不被允许反向移动。对于任务密集的电子行业，可适当调大。
- `acceptedCountLimit`：默认值为 `1000`。在每一次 Move 选择时，系统会评估周边这么多邻域解，选出最好的一个。

**Phase 注入代码**：
```java
LocalSearchPhaseConfig lsPhaseConfig = new LocalSearchPhaseConfig()
    .withLocalSearchType(LocalSearchType.TABU_SEARCH)
    .withAcceptorConfig(new AcceptorConfig().withEntityTabuSize(tabuSize))
    .withForagerConfig(new ForagerConfig().withAcceptedCountLimit(acceptedLimit));
```

### 4.5 动态插单与重排机制 (Rescheduling / Pinning)

**需求**：支持紧急订单插入，同时不改变正在生产或 2 小时内即将生产的计划。

**实现机制**：
- 引入 **Planning Pin** 机制。在传入求解器的数据模型中，遍历所有任务。
- 若 `task.getActualStartTime() < current_time + 2_hours`，则将该实体标记为锁定状态（如通过 `@PlanningPin` 注解的方法返回 true）。
- FFD 和 Tabu Search 在运行时将完全忽略被锁定的实体，仅围绕剩余资源和新增任务进行排列。

---

## 5. 权衡与决策记录 (Trade-off and Decision Records)

| ID | 决策点 | 方案选择 | 理由与考虑 | 替代方案 | 风险/妥协 |
|----|-------|----------|------------|----------|----------|
| TD-1 | LS 算法选择 | **Tabu Search** | 直观且工业界应用广泛，参数（Tabu Size）易于业务理解和干预，且性能稳定。 | 模拟退火 (Simulated Annealing) | 模拟退火对初始温度、冷却率参数极度敏感，不同规模数据下易产生不可复现的极端结果（玄学调参）。 |
| TD-2 | CH 算法选择 | **FFD** | 能够在短时间内提供一个质量远高于随机分配的可行解，降低后期约束惩罚。 | 随机分配 / 最早可用优先 | 在资源紧张时，非降序的贪心容易导致大块任务在后期无处安放（引发大量 Hard Broken）。 |
| TD-3 | Move Selector | **默认 List Move + 过滤器** | List Variable 模型必须依赖特定的 List Move（如 ListSwap）。直接使用默认结合特定过滤器能规避无意义的交换。 | 自定义深度 Move Selector | 开发成本高，且容易导致破坏原有的 OptaPlanner 内置增量评分追踪。 |
| TD-4 | 降级机制 | **策略异常时降级至默认** | 保障系统高可用性。 | 异常直接抛出，中断排产 | 影响生产业务连续性。 |

---

## 6. 已知问题与风险 (Known Issues and Risks)

### 6.1 业务与模型风险

| 风险点 | 概率 | 影响 | 缓解措施 (Mitigation) |
|--------|------|------|-----------------------|
| **FFD 导致极早期硬约束无解**：如果资源极其紧张且硬约束过多，贪婪策略可能在早期占尽资源，导致后续任务无解，初始 Hard Score 极低。 | 中 | 求解质量下降 | 允许在配置中退回默认的 Construction Heuristic，并依靠后续配置更长耗时的 Tabu Search 去纠正。 |
| **行业约束权重定义不合理**：如换线惩罚过高导致任务过度集中延误交期。 | 高 | 计划员不信任结果 | 将约束权重通过前端暴露给实施人员/计划员进行可视化调整，不硬编码在后端。 |

### 6.2 技术风险

| 风险点 | 概率 | 影响 | 缓解措施 (Mitigation) |
|--------|------|------|-----------------------|
| **多线程与落库时序问题**：Move 搜索过程异常中断导致脏数据落库。 | 低 | 数据不一致 | 排产处于全内存计算态，只有触及终止条件时，再提取 `Best Solution` 落库，过程全解耦。 |
| **升级导致历史数据重排结果不一致**。 | 必然 | 实施疑惑 | 保留固定随机种子（Random Seed）能力，并在排程快照中记录采用的算法 Profile，保证排产的“可复现性”。 |

---

## 7. 实施计划 (Implementation Plan)

遵循“配置先行，算法显式，灰度测试”的原则。

### 7.1 Phase 1：配置层支持与向后兼容 (1.5 周)
- **后端**：在 `StrategyConfigDTO` 及数据库增加算法字段（`solverProfile`, `chType`, `lsType`, `entityTabuSize`, `acceptedCountLimit`）。
- **求解入口改造**：在 `SolveManageConfigurator` 中，根据 `solverProfile` 动态装配 CH 和 LS 的 Phase，如果无配置则走原有的默认组装。
- **验证**：旧数据、旧参数在改造后运行结果与原先完全一致。

### 7.2 Phase 2：FFD + Tabu 与重排机制落地 (1.5 周)
- **算法装配**：实现 FFD 和 Tabu Search 的核心代码配置注入。
- **实体改造**：在 `TaskEntity` 增加 `@PlanningPin` 支持锁定态（针对未来 2 小时即将开工任务）。
- **权重提取**：通过 `ConstraintConfiguration` 实现各软硬约束的权重数据库读取。
- **验证**：单测覆盖，能够验证系统正确识别锁定任务并仅处理增量任务。

### 7.3 Phase 3：压测对比与行业灰度验证 (2 周)
- **数据集准备**：准备电子元器件（关注换线指标）、轮胎制造（关注硫化硬约束）各规模的数据集。
- **性能评估**：在相同硬件下，对比 “默认策略” 与 “FFD+Tabu策略”。
- **验收指标**：P95 求解时间增加不超过 15%，总体换线时间减少 20%+，硬约束违背率为 0。
- **上线**：全量上线前在 10% 的高配租户进行 A/B 测试。

---

## 8. 未来演进 (Future Evolution)

1. **元启发式算法矩阵**：当面临更复杂行业时，如果 Tabu 容易在平坦搜索空间（Plateau）停滞，可以无缝增加 Late Acceptance 阶段或切换算法组合进行多线程竞速。
2. **AI/ML 预测耗时注入**：将基于机器学习的任务耗时预测结果（替代人工填写的标准工时）注入排产模型，让排产更贴近实际产能。
3. **基于指标自动调参 (Auto-Tuning)**：目前禁忌长度是固定配置。未来可通过收集每晚的排产耗时与质量指标，利用强化学习动态反馈调整不同场景的 `Tabu Size` 和权重系数。

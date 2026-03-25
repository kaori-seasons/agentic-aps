# JVS-APS 约束求解器详细设计方案

> **文档类型**: 核心模块架构与详细设计文档
> 
> **所属子系统**: 智能排产引擎 (JVS-APS Constraint Solving Engine)
> 
> **核心依赖**: OptaPlanner 9.44.0.Final, Java 17

---

## 1. 背景与目标

在 JVS-APS 系统中，**约束求解器 (Constraint Solving Engine)** 是整个智能排产的心脏。虽然 ERP/MES 系统能够管理订单和物料，但它们无法解决"如何在有限的产能和复杂的工艺约束下，给出最优的生产时间表"这一 **NP-Hard** 难题。

### 1.1 为什么求解器是 APS 的核心？
- **多维冲突解决**：实际生产中，设备产能有限、订单交期紧迫、工艺路线严格前后依赖，人工排产往往顾此失彼。求解器通过数学建模，在庞大的解空间中自动寻找满足所有条件的方案。
- **目标优化**：不仅要排得出，还要排得好。求解器能根据用户配置的权重策略（`jvs_planning_strategy`），在"准时交付"、"最大化设备利用率"、"最小化制造周期（Makespan）"等冲突目标间找到最佳平衡点。
- **快速响应异常**：当发生设备宕机或紧急插单时，求解器能在几分钟甚至几秒内，基于当前实际情况输出全局最优的重排方案。

---

## 2. OptaPlanner 领域模型映射 (Domain Modeling)

要让 OptaPlanner 进行求解，必须将 JVS-APS 的业务数据结构映射为标准的领域驱动模型。

### 2.1 规划实体 (PlanningEntity) 与 规划变量 (PlanningVariable)
**`PlanTask` (对应数据库 `jvs_plan_task`)** 是排产的最小原子单元，即某订单的某道工序。它被打上了 `@PlanningEntity` 注解。

*   **`@PlanningVariable` (Resource)**: 
    *   **业务含义**：该任务最终被分配到哪台具体的设备或产线上执行。
    *   **值域 (ValueRange)**：当前工序类型所允许的所有 `ProductionResource` 集合。
*   **`@PlanningVariable` (StartTime)**: 
    *   **业务含义**：该任务计划开始的具体时间槽。
    *   **难点**：时间通常是连续的，但在求解器中，通常会被离散化为一系列的时间槽（如以分钟或小时为单位），或者通过影子变量动态推导。

### 2.2 规划事实 (PlanningFact)
这些是排产过程中不可变的基础数据或规则约束。
*   **`ProductionResource`**: 包含设备的可用产能属性。
*   **`WorkCalendar`**: 工作日历，定义了设备或人员的可用班次、休息时间、节假日等，决定了任务实际执行的跨度。
*   **`RoutingRule`**: 工艺路线规则，定义了 `PlanTask` 之间的依赖关系（如：装配必须在冲压之后）。

### 2.3 规划解 (PlanningSolution)
**`ApsSolution`** 被打上了 `@PlanningSolution` 注解。它不仅包含了当前问题的所有 `PlanningFact` (资源、日历等) 和所有待排产的 `PlanningEntity` (任务列表)，还持有当前方案的整体评分 `HardSoftScore`。

### 2.4 影子变量 (Shadow Variables) 的关键作用
在复杂的工艺依赖中，一个任务的开始时间不仅取决于它被分配的设备，还受到其**前置任务**结束时间的严格限制。
*   **机制**：采用 `@CustomShadowVariable` 或 `@AnchorShadowVariable`。
*   **级联更新 (Cascading)**：当求解器尝试改变任务 A 的开始时间或设备时，影子变量的 `VariableListener` 会立即触发，自动计算并更新 A 的结束时间，进而**级联推导**并更新所有依赖 A 的后续任务 (B, C) 的最早允许开始时间。
*   **优势**：极大地缩小了搜索空间，避免了求解器尝试大量"时间倒流"的无效解，是处理工艺网络（ES/EE/FS 关系）的核心。

---

## 3. 约束规则详细设计 (Constraint Formulation)

系统采用 **Constraint Streams API** (基于 Java 8 Stream 风格) 来编写约束，兼顾了性能和可读性。

### 3.1 硬约束 (Hard Constraints)
硬约束是物理或逻辑上的绝对底线，违反任何一条，该方案即被判定为不可行（Hard Score < 0）。

1.  **资源产能冲突 (No Overlapping)**:
    *   **逻辑**：同一台 `ProductionResource` 在同一时间段内，不能被分配两个或多个重叠的 `PlanTask`（除非明确标记为可并行加工）。
    *   **公式**：若 Task A 与 Task B 的 `Resource` 相同，且 `[StartTime_A, EndTime_A]` 与 `[StartTime_B, EndTime_B]` 存在交集，则触发惩罚。
2.  **工艺路线依赖 (Routing Dependencies)**:
    *   **逻辑**：严格遵守前置/后置关系。
    *   **类型**：
        *   **FS (Finish to Start)**：默认类型。任务 B 的 `StartTime` 必须 >= 任务 A 的 `EndTime`。
        *   **SS (Start to Start)** / **FF (Finish to Finish)**：某些特殊并行工序使用。
    *   **实现**：通常结合影子变量，硬约束层只需校验最终推导的时间是否仍然合规。
3.  **技能/设备能力匹配 (Skill/Capability Matching)**:
    *   **逻辑**：分配给任务的设备，必须具备执行该类型工序的能力（如：高精度铣削任务不能分配给普通车床）。
    *   **公式**：若 `Task.RequiredCapability` 不包含在 `Resource.Capabilities` 中，则触发惩罚。

### 3.2 软约束 (Soft Constraints)
软约束用于在所有可行解中优中选优。用户通过 `jvs_planning_strategy` 设置的权重将直接映射到这里。

1.  **最小化逾期 (Minimize Tardiness)**:
    *   **逻辑**：尽可能保证所有订单在其承诺的 `DueDate` 之前完工。
    *   **公式**：对于每个任务，计算 `Delay = Max(0, EndTime - DueDate)`。
    *   **渐进式惩罚 (Progressive Penalty)**：可以采用线性甚至二次方惩罚（`Delay * Delay * Weight`），使得系统更倾向于均匀分布小幅度延期，而不是让某个订单产生灾难性的严重逾期。
2.  **最小化制造周期 (Minimize Makespan)**:
    *   **逻辑**：鼓励尽早开工，缩短从投料到产出的整体时间。
    *   **公式**：给所有任务的 `StartTime` 施加一个微小的持续拉力惩罚，`Penalty = StartTime_Epoch_Millis * Minor_Weight`，驱使所有任务向时间轴左侧靠拢。
3.  **资源负载均衡 (Resource Load Balancing)**:
    *   **逻辑**：避免某台主力设备满负荷运转，而其他可替代设备闲置。
    *   **公式**：计算每台设备的总计划工作时长，求其与平均负载的方差。方差越大，惩罚分越高。权重由策略配置动态传入。

---

## 4. 求解策略与性能优化 (Solving Strategy & Performance)

为了在合理的时间内（通常是分钟级）给出可用的排产方案，求解阶段的配置至关重要。

### 4.1 阶段配置 (Phase Configuration)
求解过程分为两个核心阶段：

1.  **构造启发式阶段 (Construction Heuristics - CH)**:
    *   **算法**：通常采用 **First Fit Decreasing (FFD)** 或其变种。
    *   **作用**：快速从零构建一个初始的"可行解"。它会按照某种难度排序（例如，优先排期紧迫、约束最复杂的工序），将其逐一放入时间轴上目前能找到的最佳位置。
2.  **局部搜索阶段 (Local Search - LS)**:
    *   **算法**：主要采用 **Tabu Search (禁忌搜索)** 和 **Late Acceptance (延迟接受)** 交替进行。
    *   **作用**：在 CH 构建的初始解基础上，不断尝试微调（例如，交换两个任务的顺序，或者把一个任务移动到另一台设备），以寻找更优的分数。禁忌表防止算法陷入局部最优的死循环。

### 4.2 终止条件 (Termination Conditions)
必须设定明确的停止机制，防止无休止地计算下去。
*   **全局时间限制 (Time Spent Limit)**：例如强制设定最长运行 5 分钟或 10 分钟。
*   **无改善时间限制 (Unimproved Time Limit)**：如果在过去的 1 分钟（或者某个阈值）内，最佳分数 (Best Score) 没有发生任何实质性提升，则认为已收敛，提前终止求解。

### 4.3 增量分数计算 (Incremental Score Calculation)
在使用 Constraint Streams 时，当求解器尝试一个移动（Move）时，它不会重新计算整个工厂所有任务的分数。它只利用内部的增量网络，重新计算受该特定 Move 影响的那几个任务的分数（Delta）。这种机制是 OptaPlanner 性能远超普通启发式脚本的核心所在。

---

## 5. 自我辩证与反思 (Self-Dialectic & Reflection)

在设计这套求解器架构时，我们面临并解决了一些深度的技术权衡。

### 5.1 规则引擎选型：Easy Score Calculation (Java) vs Drools vs Constraint Streams
*   **反思**：最初可能考虑过纯 Java 写死逻辑（Easy Score）或采用 DRL 规则文件（Drools）。纯 Java 性能极差且不支持增量计算；Drools 虽然性能强大但学习曲线过于陡峭，维护困难且难以调试。
*   **决断**：最终选择 **Constraint Streams**。它完美兼顾了 Java 开发者熟悉的 Stream API 语法结构和底层强大的增量网络（Bavet）性能，是目前企业级 APS 约束表达的最佳实践。

### 5.2 连续时间 vs 工作日历映射的复杂性
*   **反思**：如果假设时间是连续 24 小时运转的，建模非常简单。但在制造业中，设备有班次安排（白班/夜班），工人要吃午饭（休息期），节假日停工。一个标称耗时 4 小时的任务，如果跨越了午休，实际占用时间轴的跨度可能是 5 小时。
*   **决断**：引入专门的日历转换层（Calendar Mapping）。求解器内部使用纯净的"有效工作分钟数"进行寻优，在计算具体时间戳和计算与交期的差距时，通过调用 `WorkCalendar` 接口动态跳过非工作时段。这增加了运算开销，但保证了排程的可执行性。

### 5.3 算法全局优化与人工干预 (Pinning) 的矛盾
*   **反思**：算法追求的是数学意义上的全局最优解。但车间现场情况复杂，计划员可能基于经验手动拖拽锁定（Pin）了某几个关键订单的排期。如果再次运行全局求解，算法可能会无视这些微调，打乱一切。
*   **决断**：深度集成 **Manual Pins (锁定机制)**。在模型中引入 `@PlanningPin` 注解。被标记为 Pinned 的任务将被求解器视为只读的 `PlanningFact`，在后续的增量重排（Rescheduling）中，只允许移动未锁定的任务来绕过这些"钉子"。既尊重了人工经验，又保留了算法的优化能力。

---

## 6. 归档说明

本设计方案详细阐述了 JVS-APS 系统中约束求解器的核心机制。通过将物理生产抽象为 OptaPlanner 能够理解的变量与约束，结合 Constraint Streams 的增量计算性能和灵活的多阶段搜索策略，系统具备了处理企业级复杂排产问题的能力。

后续开发需严格遵循本领域模型的映射关系，特别是在处理影子变量级联和工作日历转换时，需编写详尽的单元测试以确保基础算理的正确性。此文档将作为后端算法研发团队实施编码的直接指导基准。
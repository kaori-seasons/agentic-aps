# 第二章：领域建模与基础数据——从工厂对象到排产实体

## 总论：排产之前，先把工厂“翻译成机器能懂的语言”

APS 的第一难点，不是算法，而是建模。

如果企业只知道“这张订单很急”“这台设备比较忙”“这个工序得先做”，对人来说足够理解，对求解器来说却完全不够。求解器需要的是：

```
明确的实体
    + 明确的关系
    + 明确的时间规则
    + 明确的约束边界
```

JVS-APS 当前代码中最有价值的部分之一，就是已经把这套“翻译工作”拆成了清晰的领域对象。

---

## 一、排产问题中的核心对象

### 1.1 五类基础对象

从 `cn.windwheel.aps.solve.model` 包来看，当前系统的排产模型主要围绕以下对象展开：

| 对象 | 类名 | 作用 |
|------|------|------|
| 订单 | `ProductionOrder` | 排产需求的来源，携带交期、优先级、颜色等业务信息 |
| 任务 | `ProductionTask` | 排产的最小原子单元 |
| 资源 | `MainProductionResource` / `ProductionResource` | 任务最终要挂载到的设备或主资源 |
| 日历 | `WorkCalendar` | 资源在什么时间可以工作 |
| 策略 | `PlanningStrategy` | 方向、权重、终止条件、求解模板等配置 |

### 1.2 为什么“任务”而不是“订单”是排产原子

在生产现场，真正需要落到时间轴上的不是订单，而是订单拆解后的工序任务。

例如一张生产订单可能经过：

```
下料 → 加工 → 组装 → 检验
```

如果直接以订单作为排产单位，就无法表达：

- 前后工序依赖
- 不同工序使用不同设备
- 中间工序的等待时间
- 某一道工序已开工而后续还未排的状态

因此，`ProductionTask` 才是当前系统真正的 `@PlanningEntity`。

---

## 二、`SchedulingSolution` 如何组织整个求解世界

### 2.1 当前系统中的规划解

`SchedulingSolution` 的结构非常关键，它定义了求解器一次求解时“能看到的全部世界”：

```java
@PlanningSolution
public class SchedulingSolution {
    private LocalDateTime planTime;
    private PlanningStrategy strategy;
    private List<ProductionOrder> productionOrders;
    private List<MainProductionResource> mainResources;
    private List<ProductionTask> tasks;
    private HardSoftLongScore score;
}
```

这说明当前系统在求解时并不是直接面向数据库，而是先把数据库数据装配成一个内存中的规划解对象。

### 2.2 为什么主资源是 `PlanningEntity`

这套实现里，资源不是单纯事实对象，而是带有任务列表的可规划实体：

```java
@PlanningEntity
public class MainProductionResource {
    @PlanningListVariable(valueRangeProviderRefs = "taskRanges")
    private List<ProductionTask> taskList = new ArrayList<>();
}
```

这意味着当前模型采用的是 **List Variable 模式**：

- 资源持有任务列表
- 求解器通过“把任务插入某个资源列表中的某个位置”来探索解空间
- 任务的先后顺序和归属资源同时由列表位置表达

这比“给任务单独分配资源字段和时间字段”的传统方式，更贴近当前系统对工序链和资源序列的表达。

---

## 三、`ProductionTask`：排产系统中最重要的类

### 3.1 它承载了三层信息

`ProductionTask` 不是一个简单 DTO，而是同时承担三类职责：

#### **职责一：业务任务**

- `code`
- `order`
- `material`
- `quantity`
- `process`

#### **职责二：工艺网络节点**

- `frontTasks`
- `frontTaskCodes`
- `nextTaskCodes`
- `startTask`
- `endTask`

#### **职责三：求解状态实体**

- `resource`
- `pinned`
- `startTime`
- `endTime`

这种设计意味着：**一个任务既知道自己是什么，也知道自己依赖谁，还知道自己当前被排到了哪里。**

### 3.2 锁定机制为什么重要

当前类中直接存在：

```java
@PlanningPin
private Boolean pinned;
```

这不是一个小细节，而是 APS 实际落地的关键能力。它代表：

- 已开工任务不能被重新洗牌
- 计划员人工确认的任务可以被冻结
- 重排时，系统会围绕“钉住”的任务重新优化其余部分

### 3.3 结束时间为什么不是主规划变量

`ProductionTask` 中 `startTime` 是通过监听器驱动的阴影变量，`endTime` 则根据开始时间、资源日历与工时动态推导：

```java
public LocalDateTime getEndTime() {
    Duration taskDuration = calculateTaskDuration();
    return TaskCalendarUtils.calculateEndTime(startTime, taskDuration, resource.getWorkCalendars());
}
```

这说明当前系统的建模思想是：

- 求解器决定“任务在哪个资源列表里”
- 监听器负责计算“这个位置对应什么时间”
- 时间并不是随意填写，而是由资源顺序、前置约束和日历共同推导

---

## 四、订单是如何被拆成任务的

### 4.1 `SolveServiceImpl` 中的核心流程

当前系统在构造求解解时，先处理订单，再生成任务：

```
生产订单
   ↓
按策略规则排序
   ↓
执行 MRP 计算
   ↓
生成制造任务
   ↓
补充订单回填
   ↓
进入 SchedulingSolution.tasks
```

代码里这条主链很清晰：

1. `sortProductionOrders(...)`
2. `MrpUtils.calculateMaterialAvailability(...)`
3. `ProductionTaskUtils.generateTask(...)`
4. `addSupplementOrders(...)`

### 4.2 这一步为什么比求解本身更难

很多 APS 项目失败，不是解不出来，而是前置任务生成逻辑不稳。因为订单拆解要同时考虑：

- BOM 层级关系
- 物料齐套与否
- 是否需要补充生产
- 工艺路线的节点关系
- 已锁定任务是否已存在

一旦这一步出错，后面的约束求解再强也只是“在错误数据上做最优”。

---

## 五、基础数据页面与领域建模的映射关系

前端页面结构刚好对应了这套建模过程：

| 页面分组 | 页面 | 对应领域能力 |
|---------|------|-------------|
| `dataBase` | 物料 | `Material` |
| `dataBase` | 制造 BOM | `BomMaterial` |
| `dataBase` | 生产订单 | `ProductionOrder` |
| `dataBase` | 主资源 | `ProductionResource` |
| `dataBase` | 来料计划 | 物料供给侧输入 |
| `productionEngineering` | 工艺 | 工序能力定义 |
| `productionEngineering` | 工艺路线 | 前后工序关系 |
| `productionEngineering` | 生产日历 | `WorkCalendar` |

这意味着系统的业务 UI 与求解模型是对应的，而不是脱节的两套体系。

---

## 六、本章总结

### 核心要点

1. **排产建模的关键不是订单，而是任务。**
2. **`SchedulingSolution` 是求解器看到的完整工厂快照。**
3. **JVS-APS 采用 List Variable 模型，让资源列表顺序直接承载排产结果。**
4. **锁定任务、前后置工序、资源日历都已经进入当前实体模型。**

### 关键数字

- `SchedulingSolution` 核心要素：6 类
- `ProductionTask` 同时承担的职责：3 层
- 前端基础数据与工程配置页面：8 个

### 下章预告

第三章将进入 **求解器装配与策略模板**：当基础数据都准备好后，系统如何根据策略配置选择默认求解、`FFD + Tabu` 或 `FFD + Late Acceptance`，并真正启动一轮排产计算。

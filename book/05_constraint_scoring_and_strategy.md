# 第五章：约束评分与动态权重——从“排得出”到“排得好”

## 总论：可行解只是底线，评分函数才决定方向

排产系统如果只追求“不要冲突”，最终得到的往往只是一个勉强可执行的时间表。

真正的 APS 还必须回答：

- 是优先保交期，还是优先平衡负载？
- 是尽量压缩整体完工时间，还是减少工序间空档？
- 电子行业是不是要特别关注换型时间？

JVS-APS 当前系统通过 `ApsHardSoftScoreConstraintProvider` 和动态权重配置，把这些问题统一到同一套分数模型里。

---

## 一、当前系统的约束全景

`defineConstraints(...)` 中当前已经定义了 9 个约束：

1. `arrangeByPriority`
2. `equipmentCanOnlyHandleMatchingProcesses`
3. `finishEarly`
4. `minimizeTardiness`
5. `balanceResourceLoad`
6. `minimizeGapBetweenDependentTasks`
7. `noResourceTimeOverlapping`
8. `theEquipmentCanOnlyProcessOneProcessAtTheSameTime`
9. `minimizeSetupTime`

这组约束覆盖了 APS 系统中最核心的四类目标：

| 目标类别 | 代表约束 |
|---------|---------|
| 可行性 | 资源匹配、资源不重叠、每任务必须分配资源 |
| 交付性 | 最小化逾期、按优先级安排 |
| 效率性 | 尽早完成任务、减少依赖空档 |
| 稳定性 | 资源负载均衡、最小化换型时间 |

---

## 二、硬约束：系统不能越过的底线

### 2.1 资源能力必须匹配

`equipmentCanOnlyHandleMatchingProcesses(...)` 明确要求：

- 任务使用的主资源必须在工序允许资源列表中

否则直接进入 Hard Penalty。

### 2.2 资源时间不能重叠

`noResourceTimeOverlapping(...)` 是 APS 的物理底线：

- 同一资源上的两个任务不能在同一时间交叉执行

这类约束一旦不成立，计划就不是“不优秀”，而是“不可执行”。

### 2.3 每个任务都必须被分配

`theEquipmentCanOnlyProcessOneProcessAtTheSameTime(...)` 虽然名字略长，但本质上是在保证：

- 每个任务至少要有资源、开始时间、结束时间

换句话说，不能把问题“拖着不安排”当成一种解。

---

## 三、软约束：体现企业经营偏好的地方

### 3.1 最小化逾期

```java
delayMinutes * minimizeTardinessWeight
```

只要任务完成时间晚于订单交期，就开始处罚。这个约束适合交付承诺极强的企业。

### 3.2 尽早完成任务

系统对所有任务的最大完成时间施加软惩罚，本质上是在压缩整体制造周期。

这相当于在优化：

```
Makespan
```

### 3.3 资源负载均衡

当前系统用“任务时长平方”方式对资源负载做惩罚，意味着：

- 资源越集中，惩罚越明显
- 比简单线性惩罚更容易把任务拉向均衡状态

### 3.4 减少工序间空档

`minimizeGapBetweenDependentTasks(...)` 会关注：

- 前置工序结束后
- 当前工序是不是被放得太晚

它优化的不是交期，而是流程连续性。

### 3.5 最小化换型时间

`minimizeSetupTime(...)` 当前已经留出了行业化入口：

- 如果相邻任务物料不同，则增加惩罚
- 权重来自动态配置

这尤其适合电子、轮胎、化工等换线/换模成本敏感的场景。

---

## 四、动态权重：让同一套引擎服务不同工厂

### 4.1 当前默认权重

`ConstraintWeightConfigDTO` 中给出了默认值：

| 权重项 | 默认值 |
|------|-------|
| `priorityWeight` | 50 |
| `finishEarlyWeight` | 1 |
| `minimizeTardinessWeight` | 1 |
| `balanceResourceLoadWeight` | 1 |
| `minimizeGapBetweenDependentTasksWeight` | 1 |
| `minimizeSetupTimeWeight` | 0 |

这里最有意思的是最后一个字段：**换型时间默认关闭**。

这说明当前系统的默认模板更偏通用制造场景，而不是默认假设企业一定存在高 setup cost。

### 4.2 为什么 `priorityWeight` 默认最高

这反映了当前系统的一个业务判断：

在大多数工厂里，任务优先级往往是计划员最直接、最稳定的经营意图表达。

所以系统默认更强调：

> 高优先级任务尽量更早被安排。

### 4.3 动态上下文的实际意义

`DynamicConstraintWeightContext` 让每次求解都可以带着自己的权重模板进入约束计算。

这意味着：

- 一个租户可以有多套策略
- 不同时间窗口可以切换不同排产风格
- 系统不需要改代码，只需调整策略

---

## 五、如何针对行业做策略差异化

### 5.1 交期导向型企业

建议提高：

- `minimizeTardinessWeight`
- `priorityWeight`

适合：

- 客户罚款高
- 插单频繁
- 交期违约代价大的行业

### 5.2 设备均衡导向型企业

建议提高：

- `balanceResourceLoadWeight`
- `finishEarlyWeight`

适合：

- 多台同类设备并行
- 瓶颈不固定
- 希望产线使用率更均衡

### 5.3 高换型成本行业

建议打开并提高：

- `minimizeSetupTimeWeight`

适合：

- 电子元器件
- 轮胎
- 有明显换模、换刀、换配方成本的生产环境

---

## 六、本章总结

### 核心概念

1. **硬约束负责“不能错”，软约束负责“尽量好”。**
2. **当前系统已具备一套完整的 APS 基础评分框架。**
3. **动态权重配置让系统从“单一算法”变成“可调度平台”。**
4. **行业差异化并不一定先改代码，很多时候先改权重。**

### 关键数字

- 当前定义约束总数：9 个
- 动态权重字段：6 个
- 默认关闭的行业化权重：1 个（换型时间）
- 默认最高软约束权重：50（按优先级安排）

### 下章预告

第六章将回到用户工作台，讲解 **甘特图、人机协同与任务调整**：为什么求解器给出的结果还需要预览、确认、锁定、拆分、移动，以及当前系统如何把这些操作串成一个真正可落地的生产闭环。

# OptaPlanner / Timefold 双引擎

## 架构与兼容性

本次是双引擎并存改造，不是替换 OptaPlanner。

- 保留 `org.optaplanner:optaplanner-core:9.44.0.Final`。
- 新增 `ai.timefold.solver:timefold-solver-core:1.16.0`，使用 Core 手工创建求解器，不引入 Spring Boot Starter。
- 保持 Java 17、Spring Boot 2.7 和现有策略 JSON 数据列，不需要修改数据库结构。
- `PlanningSolverEngine` 隔离引擎创建；`OptaPlannerEngine` 与 `TimefoldV1Engine` 分别组装和运行自己的求解器。
- `SolveManageConfigurator` 根据模板路由引擎，按租户维护会话。不同租户可以同时运行不同引擎，同一租户只允许一个活动会话。
- `ManagedSolverSession` 提供统一提交、等待、取消和关闭操作。求解结束后释放会话，应用关闭时回收剩余会话。
- 两套引擎使用同一套业务实体。规划注解分别标记，分数字段独立，监听器通过 `SchedulingScoreDirector` 共享正排、逆排、日历和工序依赖计算。
- Timefold 的结果在引擎边界转换为历史分数类型，后续预览、确认、派工和保存流程不需要切换数据格式。

这里的“并行”指两套引擎共存并可跨租户并发运行。不会自动为同一次生产请求运行两套引擎并保存两份结果，也没有自动选择获胜引擎。

## 全部预置模板

| 模板 | 引擎 | 局部搜索 | 未指定的每步采纳候选数 |
| --- | --- | --- | --- |
| `DEFAULT` | OptaPlanner | 引擎默认阶段 | 引擎默认 |
| `FFD_TABU` | OptaPlanner | 禁忌搜索 | 1000 |
| `FFD_LATE_ACCEPTANCE` | OptaPlanner | 晚期接受 | 1000 |
| `TF_TABU` | Timefold | 禁忌搜索 | 1000 |
| `TF_LATE_ACCEPTANCE` | Timefold | 晚期接受 | 1 |
| `TF_SIMULATED_ANNEALING` | Timefold | 模拟退火 | 1 |
| `TF_GREAT_DELUGE` | Timefold | 大洪水 | 1 |
| `TF_TABU_OSCILLATION` | Timefold | 禁忌接受器 + Strategic Oscillation podium | 1000 |

历史数据中缺失或为 `null` 的模板按 `DEFAULT` 处理，不自动迁移到 Timefold。

两个引擎都使用适配 `PlanningListVariable` 的构造阶段，不强行设置仅适用于标量变量的 `FIRST_FIT_DECREASING`。历史 `FFD_*` 模板保持标识兼容，其候选任务按已有难度比较器降序排列。禁忌搜索作用于移动的任务值，而不是封锁整个资源实体。

所有 Timefold 模板都有明确的构造和局部搜索阶段，不通过枚举占位或默认回退代替实现。社区版禁用 move 多线程，不需要商业许可。

## 策略设置与回退

策略编辑页可选择带有引擎标签的模板，展开高级参数设置接受器参数、总时限、随机种子和软约束权重。未展示或未来扩展的 JSON 配置会在编辑时保留。

示例配置：

```json
{
  "direction": "FORWARD",
  "materialConstrained": true,
  "solverProfile": "TF_SIMULATED_ANNEALING",
  "maxSolveTimeSeconds": 60,
  "maxNoImprovementTime": 10,
  "acceptedCountLimit": 1,
  "simulatedAnnealingStartingTemperature": "0hard/1000soft",
  "randomSeed": 7,
  "constraintWeightConfig": {
    "priorityWeight": 50,
    "finishEarlyWeight": 1,
    "minimizeTardinessWeight": 10,
    "balanceResourceLoadWeight": 1,
    "minimizeGapBetweenDependentTasksWeight": 1,
    "minimizeSetupTimeWeight": 0
  }
}
```

切换回历史模板即可让下一次排程使用 OptaPlanner；不会改写已确认计划，也不会在求解失败时静默切换引擎。

参数默认值：

- 禁忌长度 `entityTabuSize`：7。历史字段名保留，列表模型中对应 value tabu。
- 晚期接受窗口 `lateAcceptanceSize`：400。
- 模拟退火初始温度：`0hard/1000soft`。这是起点，不代表适合所有规模，需用实际业务数据调优。
- 大洪水水位增量比例：`0.000000005`。
- 总时限：3600 秒。
- 无改进时长为空或为零时，OptaPlanner 保留找到可行解即停止的行为；Timefold 使用 30 秒无改进限制，避免构造出可行解后跳过局部搜索。
- 总时限与无改进时限按 OR 组合。
- 随机种子控制搜索顺序；时间终止会受系统负载影响，不能保证最终解逐位一致。

约束权重在创建各自 SolverFactory 时注入，不再依赖调用线程的 ThreadLocal，因此异步求解与跨租户运行不会丢失或混用权重。

Timefold 使用列表前缀锁定保留已锁定任务。锁定任务必须有有效资源和开始时间。列表前缀不能被移动，现有日历计算继续避让锁定区间。

## 验证

```sh
mvn -f aps/pom.xml test
npm --prefix aps-ui test
npm --prefix aps-ui run build
```

新增测试覆盖：

- 全部模板创建真实求解器，旧模板与新模板的引擎路由互斥。
- 同一租户重复提交保护、跨租户双引擎共存、双引擎并发求解、会话释放。
- 所有模板在真实监听器下求解正排和逆排的小数据集，以及跨资源工序依赖。
- Timefold 锁定任务、资源分配、时间重叠与权重配置。
- 算法参数装配、非法参数、历史策略 JSON 兼容。
- 取消提交与取消已运行任务。
- 前端策略编辑回显、配置无损保存和参数校验。

这些测试是回归检查，不是生产性能基准。上线前仍需对真实资源规模、日历、物料、合并/拆分任务和工序依赖做双引擎基准与验收。两引擎的重叠惩罚计算精度不同，不应直接把原始分数作为引擎质量排名。

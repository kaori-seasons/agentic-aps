# Timefold 与小模型组合排产的实现与部署

当前后端通过现有智能排产入口调用两个引擎。历史策略默认使用 OptaPlanner，选择 `TIMEFOLD` 的策略支持结构化动态事件、历史队列种子和列表前缀锁定。小模型先生成待审核候选，计划员明确提交后进入求解。两个引擎都必须得到初始化完成、硬分数为零的解，才能保存待确认计划。

Timefold 固定为 1.8.1，兼容项目当前 Java 17、Spring Boot 2.7 和 SLF4J 1.7 运行环境。1.16.0 在该日志栈下出现过运行时方法缺失，升级 Timefold 时需要同时验证日志栈。策略模板名称沿用已有的 `FFD_TABU` 和 `FFD_LATE_ACCEPTANCE`。构造阶段采用引擎支持的列表变量构造方式，随后使用 Tabu 或 Late Acceptance。

**部署顺序**

1. 在应用发布前，执行 `aps/sql/20261113_hybrid_solve_audit.sql` 创建求解审计表。
2. 用 Java 17 和 Maven 在 `aps` 目录运行 `mvn clean verify`，部署生成的后端包。
3. 通过已有策略维护接口保存下面的策略配置，使用实际资源和日历完成业务验收。
4. 配置小模型服务。默认关闭模型调用，结构化事件入口仍可使用。

```json
{
  "direction": "FORWARD",
  "materialConstrained": true,
  "solverEngine": "TIMEFOLD",
  "solverProfile": "FFD_LATE_ACCEPTANCE",
  "solveTimeSeconds": 120,
  "maxNoImprovementTime": 20,
  "randomSeed": 17,
  "entityTabuSize": 7,
  "lateAcceptanceSize": 400,
  "acceptedCountLimit": 1000,
  "constraintWeightConfig": {
    "priorityWeight": 50,
    "finishEarlyWeight": 1,
    "minimizeTardinessWeight": 100,
    "balanceResourceLoadWeight": 1,
    "minimizeGapBetweenDependentTasksWeight": 1,
    "minimizeSetupTimeWeight": 10
  }
}
```

复现时，设置固定的 `randomSeed` 与 `scoreCalculationCountLimit`，并使用审计中的问题快照。评分次数预算会替代日常墙钟预算和无改进终止条件，同时保留一小时安全上限。相同引擎版本、输入队列和算法配置在安全上限触发前可做结果回放对照。仅固定种子、按墙钟终止的不同运行可能停在不同搜索步骤。

```bash
export APS_SMALL_MODEL_ENABLED=true
export APS_SMALL_MODEL_ENDPOINT=http://127.0.0.1:11434/v1/chat/completions
export APS_SMALL_MODEL_NAME=qwen2.5:7b
export APS_SMALL_MODEL_TIMEOUT_SECONDS=20
export APS_SMALL_MODEL_MAX_CONTEXT_TASKS=200
# LM Studio 等要求 JSON Schema 的服务使用：
export APS_SMALL_MODEL_RESPONSE_FORMAT=JSON_SCHEMA
# 服务需要鉴权时，另行设置 APS_SMALL_MODEL_API_KEY。
```

模型服务需支持 JSON 格式的 Chat Completions 协议。默认使用 `JSON_OBJECT`，可切换 `JSON_SCHEMA`。Schema 按事件类型限定时间字段和当前租户目标 ID；应用仍独立检查这些约束，不能假定推理服务会严格执行 Schema。客户端使用 HTTP/1.1，避免部分本地服务无法处理 h2c 升级而挂起。调用包括连接超时、整次请求超时、128KB 响应上限与关闭重定向。模型输出经严格 JSON 解析、字段验证和当前租户引用检查，服务异常或输出不合格时返回业务错误。后置解释失败时保留结构化差异摘要。

结构校验不能判断模型是否准确理解了业务原意。真实 Qwen2.5-0.5B Q8_0 在早期验收中未通过事件提取质量门槛。随后使用用户 `~/.zshrc` 中的 MiniCPM5-2B，真实完成三事件提取、核对、Timefold 重排、模型解释和确认入库，见 [组合优化验收记录](e2e/minicpm-report.json)。当前样本通过不等于生产语义准确率已验收，仍须扩充实际业务样本；候选须核对后再提交求解。默认生产配置关闭模型，隔离测试通过环境变量显式开启。

可用 `zsh aps/scripts/run-minicpm-e2e.zsh` 加载 `MINICPM_NEW_*` 并启动隔离后端，使用 `python3 aps/scripts/e2e_minicpm.py` 验证完整闭环。`e2e-local.yml` 支持标准 `APS_SMALL_MODEL_*` 环境变量覆盖；凭据只在启动时读取，不写入仓库。`JSON_OBJECT` 模式也会把完整事件 Schema 交给模型，服务独立执行结构与业务校验。模型 HTTP 状态、连接错误与无效 JSON 分别报告，不回显服务端敏感响应。

鉴权默认仅接受登录会话，`aps.auth.allow-header-auth` 和 `aps.auth.allow-query-fallback` 均为 `false`。需要可信网关身份头的现有部署须明确配置，并确保入口清除外部身份头。普通客户端不能通过请求头或查询参数自授租户与管理员身份。

**接口顺序**

接口路径以下列业务前缀为基准。完整 HTTP 路径还包括项目的 `/mgr/jvs-aps` 上下文。调用沿用项目现有登录和权限机制。

先通过 `POST /smart-scheduling/presolve` 将自然语言事件转成候选。下列时间和资源编码均为演示，实际请求需替换成当前业务数据。

```json
{
  "narrative": "设备R1在2026年11月14日10点到12点停机，请评估受影响计划",
  "freezeUntil": "2026-11-14T10:00:00"
}
```

接口返回 `DynamicSchedulingRequest`。计划员核对资源、时间窗、影响订单和种子后，将候选作为 `dynamicRequest` 提交到原有 `POST /smart-scheduling/generate`。也可直接提交经过审核的结构化事件。

```json
{
  "planningStrategyId": "实际策略ID",
  "dynamicRequest": {
    "freezeUntil": "2026-11-14T10:00:00",
    "events": [
      {
        "type": "RESOURCE_UNAVAILABLE",
        "targetId": "实际资源ID",
        "start": "2026-11-14T10:00:00",
        "end": "2026-11-14T12:00:00"
      }
    ],
    "seeds": [],
    "recommendedProfile": "FFD_LATE_ACCEPTANCE"
  }
}
```

| 事件 | 引用对象 | 必需的时间字段 | 实际行为 |
| --- | --- | --- | --- |
| INSERT_ORDER | 当前租户可排池中的订单 ID | 无 | 提高候选订单优先级，按其 BOM 与工艺候选资源扩充影响面 |
| RESOURCE_UNAVAILABLE | 当前租户资源 ID | start、end | 关闭该资源的指定时间窗，重新分配资源或时段 |
| MATERIAL_DELAY | 当前租户物料 ID | availableAt | 将消耗该物料的工序设置最早可开工时刻 |
| DUE_DATE_CHANGE | 当前租户可排池中的订单 ID | deliveryTime | 用新交期重新评分，确认后更新订单交期 |

插单订单须先通过已有订单维护流程建立，并具备可用 BOM、工艺和资源。模型可推荐软约束权重，结构化请求使用 `recommendedWeights` 提交。每项权重需在 0 到 1000000 之间。种子只允许引用本次任务与兼容资源，重复任务、冻结任务和不匹配资源会被拒绝。

生成后，继续使用现有进度、预览、确认和取消接口。完成日志包含求解审计 ID，可通过 `GET /smart-scheduling/audit/{planId}` 读取当前租户的输入快照、问题哈希、评分、配置和任务差异。

**约束与时间推导**

两个引擎共享资源匹配、冲突区间、ES/EE 前后置、冻结状态及换型相邻关系的业务语义。软评分读取问题中的权重快照，脱离调用线程的 ThreadLocal。换型评分以时间上相邻且物料不同的任务计算切换次数，连续无间隔任务同样计入。

重排只将主订单作为独立需求，补充订单由 MRP 生成一次。冻结任务与新任务按补充订单业务编码统一订单引用，防止重复保存。Timefold 用列表前缀保留冻结任务；OptaPlanner 9 将冻结任务作为列表外的问题事实参与资源冲突与时间推导。结果门禁要求初始化分数严格为零。

列表变更后，时间监听器依据当前资源队列和工艺图，确定性地重新推导活动任务时间。资源顺序与工艺依赖构成有向图，正排按拓扑顺序计算，倒排按反向顺序计算。候选中的环形依赖保留空时间，由硬约束拒绝。该计算复用现有班次、工序间隔、物料等待和 EE 缓冲计算，避免试探移动撤销时残留上一候选解的时间。

| 硬约束 | 实现 |
| --- | --- |
| 完整分配 | 活动任务具有资源、开始时间和完成时间 |
| 资源能力 | 分配资源属于该工序的可用资源集合 |
| 资源时段 | 同一资源的任务区间不重叠 |
| 工艺依赖 | ES/EE 关系及工序前后间隔、缓冲得到检查 |
| 物料可用 | 动态延迟工序不能早于 availableAt |
| 设备可用 | 任务不能与资源不可用时间窗相交 |
| 冻结保护 | 冻结任务的资源、开始与完成时间保持原值 |

正排和倒排会绕开冻结任务及设备不可用窗口。冻结任务作为固定时间障碍保留，未来固定任务之前的可用空档仍可使用。倒排场景中，如果物料可用时刻与既有交期形成不可行条件，会拒绝候选，计划员可调整交期或选择正排重新评估。

影响面以直接关联订单及其共用候选资源保守扩展。在制、已完成、人工锁定、早于冻结边界及影响面外的已有任务会固定。影响范围由实际工艺和资源关联决定，任务高度耦合时可能涉及较大范围。求解采用一次受控快照上的局部重排，新的事件通过新的候选求解进入。

**确认与审计**

生成请求在异步投递前预留租户会话，避免两个请求同时进入求解。取消标记与具体计划 ID 绑定，求解期间每 250ms 检查一次，支持取消请求与求解任务落在不同节点的情况。迟到工作线程仅能删除自己持有的预留令牌。候选写入、确认和取消使用同一租户锁进行协调。

确认采用 REPEATABLE_READ 事务，对参与排产的订单、物料、路线、日历、资源和任务输入范围加锁，重新计算数据签名。与求解快照有差异时拒绝确认。输入表需具备合适的租户索引，确认大批量计划时应检查锁等待和执行计划，避免范围扫描扩大锁定成本。

审计表持久保存完整输入和有效问题快照，包括实际计划时刻、任务标识、资源队列、冻结位置、配置与种子。问题快照通过编码重建任务引用，支持离线回放。结果保存硬软评分、已有任务的位置变化和解释性摘要。模型后置文字标记为解释性建议，业务人员可以核对原始差异。SQL、模型服务配置和业务负载验收共同组成部署步骤。

**验证范围**

回归覆盖两个引擎、正排倒排、两个策略模板、冻结空档、设备时间窗、物料延迟、跨引擎评分一致性、快照回放、取消、结构化事件引用与权重校验、来源数据变更阻断，以及本地 HTTP 服务返回畸形或超限内容的情形。多资源与工序依赖场景使用 FULL_ASSERT 检查试探移动撤销和评分一致性。

后端 39 项回归测试通过，本地 HTTP 测试服务覆盖异常协议与网络失败；另使用真实 MiniCPM5-2B 与隔离数据库完成组合优化闭环，记录见 [端到端验收报告](端到端验收报告.md)。实际客户数据规模、现场数据库锁等待及模型事件识别准确率，仍需要在部署环境用代表性订单和更完整的事件样本验收。默认生产配置关闭模型，运行中的隔离后端已显式启用 MiniCPM。

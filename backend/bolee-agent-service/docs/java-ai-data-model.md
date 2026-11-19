# Java AI 客服数据模型设计

## 目的与边界

本文定义 AI 客服 DTO/DO 与 SQL 的共同基线；聊天接口已实现字段以[客户聊天 API 数据契约](customer-chat-api.md)为准，本文较早的样例不再作为当前 wire contract。产品资料审核、理赔与人工客服中尚未实现的能力仍按目标契约维护，不代表已有运行接口。

数据归属：Java MySQL 保存客户会话目录、轮次幂等元数据，以及为幂等重放而采用 AES-GCM 加密的完整客户响应；Agno PostgreSQL 保存消息、Agent run 和运行 trace；产品/保单数据始终由保险 Java 服务提供。Java 不保存明文聊天请求，密文响应与会话轮次一同过期和删除。工程初始策略为会话/轮次在最后活动 90 天后过期、无正文 trace 保留 30 天、已完成清理任务保留 30 天；备份滚动保留 35 天。启用持久化写入前必须配置对应期限、`AI_IDEMPOTENCY_ENCRYPTION_KEY`（32 字节 AES key 的 Base64 表示），并实现 Java 与 Agno 两侧的删除/清理。

## 类型和标识约定

| 字段 | 生成方 | Java 类型 | JSON | 用途 |
| --- | --- | --- | --- | --- |
| `sessionId` | Java 客户 API | `String` | UUID/不透明字符串 | 客户可见会话标识；必须按 owner 查找。 |
| `agnoSessionId` | Java | `String` | UUID 字符串 | Agno 内部会话 ID，不回传客户。 |
| `clientMessageId` | 前端 | `String` | UUID 字符串 | 同一会话内用户消息幂等键。 |
| `turnId` | Java | `String` | UUID 字符串 | Java 内部业务轮次 ID；当前不传给 Python。 |
| `runId` | Agno | `String` | 字符串 | AgentOS 执行 ID，不代替 `turnId`。 |
| `traceId` | Java | `String` | UUID 字符串 | Java 内部链路关联 ID；当前不传给 Python。 |
| `requestId` | 调用方 | `String` | UUID 字符串 | 一次内部工具调用 ID。 |
| 数据库主键 | MyBatis-Plus | `Long` | 十进制字符串 | 只在 Java 数据层使用。 |

金额使用 `BigDecimal`，禁止 `float/double`；外部时间使用 ISO-8601 UTC。DO 继承 `BasePojo`，其中自动填充时间为 `LocalDateTime.now()`，所以 MySQL `DATETIME(3)` 保存应用 JVM 默认时区的本地时间；同一环境的所有实例必须固定相同 JVM 时区。API 边界转换为 `Instant`/UTC，不能把 `LocalDateTime` 直接标成 UTC。客户 ID 只由 Java 登录态产生，客户端和模型不得提交作为授权依据。

## DTO 分层

新功能边界统一使用 `*RequestDTO`、`*ResponseDTO`、`*DTO`，持久化对象使用 `*DO`。现有 `*VO` 保留兼容，不把数据库 DO 直接作为 API 响应。所有外部 DTO 使用显式字段白名单。

### 客户 ↔ Java

当前客户请求与响应样例、状态、JSON 字段名及响应 envelope 见[客户聊天 API 数据契约](customer-chat-api.md)。客户请求不含 `userId`、`agnoSessionId`、授权令牌、工具参数或保单对象 ID。Java 认证后生成/校验会话归属，并在自身数据库记录 `turnId` 和 `traceId`。

### Java ↔ Python

当前 `POST /internal/chat` 请求实际使用 `user_id`、Agno 内部 `session_id`、短时 `authorization_context` 和 `message`；不传 `clientMessageId`、Java `turnId` 或 Java `traceId`。Python 响应及 Java 映射字段见[客户聊天 API 数据契约](customer-chat-api.md)。Python 返回 Agno `run_id`，Java 保存它与自己 `turnId` 的映射。Python 只将授权上下文交给工具依赖，不放入提示词。

Java 调 Python 的 history/delete 是服务内部管理接口，只接受 Agno 内部会话 ID；调用前由 Java 校验 customer owner。Python 到 Java 的工具接口同时要求 OAuth2 Bearer token 和 `X-Agent-Authorization-Context`；服务 scope 由 OAuth client 约束，客户身份和授权范围从 Java grant 解析，不接受请求体中的用户 ID。

会话客户 DTO 为 `AiConversationSummaryDTO`、`AiConversationMessagesResponseDTO`、`AiMessageDTO`、`AiConversationDeleteResponseDTO`；消息角色只允许 `USER`/`ASSISTANT`。Python 私有适配 DTO 为 `AgentHistoryPageDTO`、`AgentDeleteSessionResponseDTO`。分页游标不透明；Java 返回前过滤工具消息、内部 trace 和隐藏推理。`AiErrorDTO` 统一承载 `code`、安全消息和 `traceId`。

### 产品推荐与报价 DTO（下一阶段）

`ProductRecommendationRequestDTO`：`requestId`、`turnId`、`criteria`、`limit`。V1 `criteria` 只允许 Java 当前发布的 `categoryId`；后续才可加经产品规则确认的覆盖偏好和年龄。不得定义自由字段 map、SQL、模型生成 URL 或健康史字段。预算暂只作为偏好；价格币种和统一周期换算没有来源，不能作为硬筛选。

产品推荐与比较的当前结构以[产品推荐与比较专项计划](product-recommendation-plan.md)和[客户聊天 API 数据契约](customer-chat-api.md)为准。早期的 `coverageHighlights`、`importantLimits`、`detailUrl`、`quote`、顶层 `sources` 样例未进入当前聊天 DTO。

`ProductQuoteDTO`：`amount: BigDecimal`、`currencyCode`、`paymentFrequency`、`asOf`、`quoteId`。报价需 Java 通过专用应用服务执行产品规则；不能让 Agent 直接调用投保 `doPremium`，也不能把产品默认基础价冒充客户报价。

### 产品资料来源 DTO

`ProductSourceDTO` 包含 `sourceId`、`productId`、`productVersion`、`documentId`、`title`、`section`、可空 `page`、批准的 `url`、`contentHash`。Java 产品资料发布记录是权威数据；只有已审核发布且当前有效的版本进入 Python 检索索引。当前系统没有审签/版本工作流，相关 DTO 先作为目标契约，资料管理未建设前不启用。

### 本人保单 DTO（第二阶段）

`OwnPolicySummaryRequestDTO` 不含 userId；可含 `requestId`、`turnId`、可选不透明 `policyId`。`OwnPolicySummaryResponseDTO` 包含 `asOf` 和 `policies[]`。`PolicySummaryDTO` 首版白名单为 `policyId`、`productId`、`productName`、`policyStatus`、`premiumAmount`、`paymentFrequency`、`coverageStartDate`、`coverageEndDate`、`effectiveAt`；`asOf` 由响应级提供。无权威来源的 `currencyCode`、`productVersion`、`renewalDate` 不进入首版 DTO。首版只查当前登录客户作为投保人、状态 0/1/2 的保单；关系人及历史保单等归属规则未开放。

## Java MySQL 持久化模型

放在已有 `bolee-insurance` 库；表名前缀沿用 `tab_`。`BasePojo.id` 显式使用 MyBatis-Plus `ASSIGN_ID` 雪花策略。AI 元数据表通过 `owner_user_id` 做强制归属过滤，不使用旧租户 `company_no`、本人权限 `create_by` 或部门 `data_dept_no` 拦截器；这些表名已在 MyBatis 配置中显式忽略。所有 Mapper 查询必须带 `owner_user_id`（后台清理任务除外），不能依赖拦截器保护。DDL 保留 `BasePojo` 自动填充的创建人/修改人列。时间列保存与 `LocalDateTime` 一致的应用本地时间。

DDL 的唯一权威源为 [`insurance-app/src/main/resources/db/migration`](../../bolee-insurance/insurance-app/src/main/resources/db/migration) 下的版本化 SQL，通过保险服务的 Flyway Maven 插件执行。本文不再复制 SQL，避免与迁移脚本漂移。清理任务租约由 V02 单独增加，不修改已经执行过的 V01；V02 已应用到本机开发库，其他环境需备份后分别迁移。

`tab_ai_conversation.status`: `ACTIVE`、`CLOSED`、`DELETE_PENDING`、`DELETED`。`tab_ai_turn.status`: `PROCESSING`、`COMPLETED`、`FAILED`。相同会话和 `clientMessageId` 携带不同 `request_hash` 时返回冲突；相同 hash 重试返回原 turn 状态/结果，不重复启动 Agent。必须另建可重试的 Java→Python 删除/清理任务；过期后先停止客户访问，再清除 Agno 会话与 trace，成功后物理清理元数据。该清理依赖策略配置，不能只靠 MySQL 行过期。

`tab_ai_cleanup_task.status`: `PENDING`、`PROCESSING`、`RETRY_WAIT`、`COMPLETED`、`DEAD_LETTER`。删除/过期流程先关闭客户访问并在同一 Java 事务中创建清理任务；任务按退避策略重试 Agno session 与关联 trace 的删除，完成后才允许清理会话/轮次元数据。活动任务的 `expires_at` 为空，成功完成后按配置期限计算。`attempt_count`、`next_attempt_at`、`last_error_code` 保留最小运维追踪，不存外部响应正文。

请求摘要由 `AiRequestFingerprint.sha256ChatMessage` 计算：当前 V1 输入按 UTF-8 原样编码，前缀为 `ai-chat:v1\n`；不 trim、不做 Unicode 归一化，避免不同请求被合并。新增会影响结果的请求字段时必须升版本并按固定字段顺序序列化。插入依赖唯一键实现并发幂等：相同键、不同 hash 为冲突；相同 hash 且处理中返回原 turn 状态；失败是否允许重试不得复用原 turn。已完成请求只保存 `agno_run_id` 结果指针，不复制回答正文。Java 需要通过 AgentOS 按 run/turn 读取已完成结果；该读取能力依赖后续 Java-Python 协议，不在本阶段声称已可重放回答。

不在 `tab_ai_turn` 存聊天正文或完整回答；若产品/合规要求业务审计副本，应另行定义独立审计表、字段与期限。

## 不创建的类型与实施门槛

### Migration 与配置

Flyway 使用 `insurance-app/src/main/resources/db/migration` 下的版本化 SQL，通过 Maven 插件运行，不随应用启动自动迁移。当前目标 MySQL 为 5.7.36；固定 Flyway Maven Plugin 7.15.0，因为 Flyway 8 社区版已不接受 MySQL 5.7。迁移使用兼容 MySQL 5.7 的 `utf8mb4_unicode_ci`，状态枚举由 Java 类型约束。运行前从 Nacos/secret store 确认保险库，并通过环境变量提供 `AI_DB_URL`、`AI_DB_USER`、`AI_DB_PASSWORD`；不要把凭据写入仓库。先运行 `mvn -pl bolee-insurance/insurance-app flyway:info` 检查目标；已有非空保险库首次执行 `mvn -pl bolee-insurance/insurance-app flyway:baseline`（插件配置基线版本 0），之后执行 `mvn -pl bolee-insurance/insurance-app flyway:migrate`。新空库直接 migrate。插件禁用 `clean`，迁移前先备份并确认 schema。

建议配置样例（仅为工程基线，不是法规统一期限）：

```yaml
ai:
  persistence:
    enabled: true
  retention:
    conversation-days: 90
    trace-days: 30
    cleanup-task-days: 30
```

项目 `bootstrap.yml` 默认 `ai.persistence.enabled=false`。此开关只控制 Java 会话元数据和清理 outbox，不控制已在运行的 Agno PostgreSQL 会话/运行数据或 AgentOS tracing。启用后，`AiRetentionProperties` 对期限做必填且大于零的启动校验；不会静默采用代码默认值。备份期限由数据库运维策略单独配置。Java 删除请求会先将随机 Agno session UUID 追加并 fsync 到 `AI_DELETION_LEDGER_PATH`，再提交 SQL 删除状态；账本只包含 UUID，不包含用户标识或对话内容。账本必须放在**不随 SQL/Agno 数据库备份一起回滚**的持久化位置，并至少保留至所有可能恢复的备份过期且恢复演练结束。AgentOS 删除 HTTP 适配会删除对应 session/runs 与 traces，过期 trace 由 Python 内部清理接口按 Java 配置分批清理。

- 理赔状态与人工转接 DTO：等权威理赔/客服系统、接口和状态码确认后再定，当前仓库没有来源。
- Claims/customer relation IDs 不允许先以通用字段形式放进 DTO。
- 初始工程策略：会话/轮次在最后活动 90 天后过期；不含消息内容的 trace 保留 30 天；已完成清理任务保留 30 天；备份滚动保留 35 天。若 trace 含有提示词、回答或保险数据，则按会话正文的 90 天策略处理。实际配置必须符合个人信息处理告知、最短必要期限和适用的保险监管要求。
- `ai.persistence.enabled` 默认关闭。启用时必须显式配置 `ai.retention.conversation-days`、`ai.retention.trace-days`、`ai.retention.cleanup-task-days` 正整数；缺失或小于 1 时服务启动失败，不提供隐式保留天数。备份期限由数据库运维策略配置，不由 Java 属性代替。
- DDL 迁移由 Flyway Maven 插件显式执行，不在应用启动时自动建表。当前保险库已有数据时，首次执行前必须核实 Nacos 中实际 datasource、备份目标库，并只对已确认的保险库执行 `flyway:baseline`（version `0`），随后执行 `flyway:migrate`；空库直接 migrate。迁移源文件及命令见 `bolee-insurance/insurance-app`。
- 个人删除先立即禁止 Java 会话访问，处理中轮次完成或超时失败后，再通过清理任务删除 AgentOS session/runs 和对应 traces；Agno session API 本身不会删除独立 trace，因此 Python 清理适配显式删除两类数据。会话元数据仅在远端操作成功后删除。周期任务也按 conversation-days 删除超期 Agno sessions/runs（包括启用元数据追踪前创建的旧会话），并按 trace-days 分批清理 traces/spans。备份副本按运维保留周期到期，恢复备份时必须重新应用删除标记。
- AgentOS 删除调用使用独立 `agent.operator` OAuth 客户端，不能复用只具有 `agent.chat` 或产品目录 scope 的凭据。Python `/internal/sessions/{session_id}` 和 `/internal/retention/traces` 路由只接受该 operator scope；Java 先执行 owner 校验并从已存会话记录取得内部 Agno session ID。
- 恢复流程必须先恢复 SQL 与 Agno 数据库，再在隔离恢复环境启动一次 Java 清理进程并显式设置 `AI_DELETION_LEDGER_REPLAY_ON_STARTUP=true`。该模式会关闭常规调度，逐条对 AgentOS 重放 session/trace 删除，再幂等删除 SQL 中恢复出的 turn、cleanup task 和 conversation；任一远端删除失败或账本损坏会使恢复进程失败，需修复后重跑。建议使用 `--spring.main.web-application-type=none`，在重放成功后再开放流量。重放不清空账本，因此多次执行安全；账本保留和备份过期策略由运维流程负责。
- Java 清理器使用单独的 `AGNO_OPERATOR_CLEANUP_OAUTH_CLIENT_ID`、`AGNO_OPERATOR_CLEANUP_OAUTH_CLIENT_SECRET` 和固定 `agent.operator` scope；该客户端必须预先注册在 OAuth 服务中，且不能与聊天客户端 ID 相同。相同 operator client ID 还需配置给 Python `AGNO_OPERATOR_OAUTH_CLIENT_ID`。仓库无法替部署方创建 OAuth 客户端或验证真实环境凭据。
- 当前实现不自动管理备份生命周期。只有在测试环境验证重复请求、回复回放、两侧删除、过期清理，以及上述恢复重放后，才可讨论生产持久化开关。

## 推荐落地次序

1. 评审并冻结上述 DTO JSON Schema、ID 语义、错误/状态码及持久化归属。
2. 在确认 Nacos 数据库目标与备份后，用 Flyway 执行首次 migration；再编写 `AiConversationDO`、`AiTurnDO`、`AiCleanupTaskDO` 的 Mapper 和 Service。
3. 再补 chat 幂等/历史/删除用例及 Java-Python 结构化工具结果透传。
4. 实现有非敏感确定规则的个性化推荐；报价作为独立能力后接。
5. 产品资料审签/版本和 policy owner-scoped 查询由其数据基础满足后实施；理赔/人工接口等待外部系统确认。

## 当前 Bean 草案

当前 `AiAssistantController`、`AgnoAssistantService`、owner-scoped conversation/turn Mapper 与 Python Agent API 已接通聊天及历史读取。公共客户请求/响应仍使用现有 `*VO`，Java↔Python 使用独立 snake_case 内部 DTO；这是当前 wire contract，不应按本设计草案假设字段已经统一成 `*RequestDTO`。Flyway V01、V02 的本机开发库状态和其他环境迁移步骤见前文；本次历史读取没有新增 DDL。

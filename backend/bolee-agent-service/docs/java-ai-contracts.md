# Java AI 客服结构与接口契约

## 文档范围

本文保留 Java 侧总体结构与待实施能力说明。当前客户聊天、结构化响应、本人保单摘要和历史读取的真实请求/响应格式以[客户聊天 API 数据契约](customer-chat-api.md)为准。客户身份、保险事实、产品筛选规则与访问授权均以 Java 为权威；Python 负责自然语言理解、澄清问题、Agent 编排和结果解释。

个性化产品推荐与本人保单查询的 Java 优先落地顺序、前置条件及验收边界见[Java AI 功能实施计划](java-feature-implementation-plan.md)。

产品推荐的可扩展条件字典、审核发布数据模型和具体示例见[Java 产品推荐与比较专项计划](product-recommendation-plan.md)；本节其余字段作为总体契约背景，实施时以专项计划为准。

当前代码与开发数据盘点见[AI 客服数据与业务能力盘点](data-inventory.md)。下文将已存在能力与建议建设的契约分开说明；本地数据库字典仅为开发快照，不能直接视为生产规则。

当前首个已实现的 Java 代码切片及其刻意收窄的范围见[Java AI 产品目录实施切片](java-ai-implementation-slice.md)。

跨客户 API、Java/Python DTO 和会话/轮次持久化字段的统一设计见[Java AI 客服数据模型](java-ai-data-model.md)。

## Java 代码分层与位置

沿用 `bolee-insurance` 现有 `insurance-app` 与 `insurance-service` 模块，AI 新代码按功能包组织；不为此重构整个保险模块。

| 层 | 建议位置 | 主要职责 |
| --- | --- | --- |
| 客户 API / DTO | `insurance-app/.../web`、`dto/ai/customer` | 登录客户聊天入口、请求校验、响应 DTO；从 `SubjectContent` 取当前用户。 |
| 内部工具 API / DTO | `insurance-app/.../web/ai/internal`、`dto/ai/internal` | 提供给 Python 的类型化内部接口；校验服务令牌和授权上下文；不暴露给浏览器。 |
| 应用用例 | `insurance-app/.../service/ai/application` | 编排聊天调用、签发授权、处理业务工具请求并映射响应。 |
| 保险业务服务 | `insurance-service/.../service/ai` | 产品推荐规则、产品事实查询、本人保单查询与数据最小化。 |
| 基础设施 | `insurance-app/.../service/ai/adapter`、现有 Mapper/Redis 适配 | Agno HTTP 客户端、Redis 授权存取、保险数据 Mapper/Repository。 |

Controller 不访问 Mapper；Python HTTP、Redis 和数据库访问不得散落在 Controller 或领域规则中。已有 `AgnoAssistantService` 集中了承接聊天与 HTTP 的职责，后续可以逐步拆成应用用例和 `AgnoClient` 适配器，不要求本阶段一次性搬迁旧代码。

## 契约一：客户聊天入口

客户入口为登录态 `POST /ai-assistant/chat`。当前接口结构、幂等行为、状态码及各场景示例见[客户聊天 API 数据契约](customer-chat-api.md)。该文档取代本节早期 `importantLimits`、`detailUrl`、`sources` 等尚未实现的示例字段；当前产品结构以 `facts`、`matchReasons`、`productVersion` 和 `verificationStatus` 为准。

Java 持久化会话目录与必要轮次元数据，不复制完整聊天正文。表包括 `tab_ai_conversation`、`tab_ai_turn` 和可重试的 `tab_ai_cleanup_task`；字段、索引及 Flyway migration 见[Java AI 客服数据模型](java-ai-data-model.md)。会话与轮次表按 `owner_user_id` 做显式归属查询，不能依赖租户/本人/部门拦截器；唯一约束覆盖 `(owner_user_id, session_id)`、`agno_session_id`、`(conversation_id, client_message_id)` 和 `turn_id`。历史读取、关闭和删除都先由 Java 校验 owner，再调用 Python 私有接口处理 Agent 历史。工程初始留存建议为会话/轮次 90 天、无正文 trace 30 天、完成的清理任务 30 天、备份 35 天；对应配置和删除边界见数据模型文档。

## 契约二：Java 调用 Python Agent

现有服务间入口为 `POST /internal/chat`，由 Java 使用 OAuth2 `client_credentials` token（scope `agent.chat`）和聊天请求调用。实际内部 DTO 只包含 Java 派生的 `user_id`、Agno `session_id`、短时 `authorization_context` 和 `message`；Java 的 `clientMessageId`、`turnId`、trace ID 存于 Java，不传给 Python。`user_id` 只供 AgentOS 隔离会话使用，不作为 Java 业务工具的授权身份；Python 工具调用必须携带授权上下文。身份必须来源于 Java 登录态，不能使用模型抽取的身份。

Python 内部响应使用 `answer` 和 `recommendations` 分离的契约。推荐候选及比较事实由 Python 每轮工具结果收集器从 Java 的类型化推荐响应收集并校验，不能要求 LLM 在自然语言回答后重新输出 JSON。Java 负责校验并转换成客户 API DTO；服务间 DTO 与客户 DTO 不共用。Agno 的 `run_id` 尚未透传，不替代 Java 的业务 `turn_id`。

## 功能到 Java 分层映射

### A. 产品推荐与比较（第一阶段）

| 层 | Java 侧结构 |
| --- | --- |
| 客户 API | `AiAssistantController` 接收自然语言消息；认证用户由 Java 登录上下文取得。 |
| 应用层 | `AiChatApplicationService` 调 Python。Python 澄清需求并产出结构化条件；Java 收到 Python 工具请求后进入推荐用例。 |
| 内部工具 API | `POST /internal/agent-tools/v1/products/recommendations`；仅服务间访问。 |
| 契约 DTO | `ProductRecommendationRequest`、`ProductRecommendationResponse`、`ProductCandidateDTO`、`MissingCriteriaDTO`。请求不含 `userId`，字段只能来自允许的枚举和范围。 |
| 业务层 | `ProductRecommendationService` 校验必要条件、按在售状态和已审核规则筛选排序，生成匹配理由和重要限制。没有当前可用发布目录返回 `CATALOG_NOT_READY`；缺字段/条件冲突返回 `NEEDS_CLARIFICATION`；条件未支持返回 `UNSUPPORTED_CRITERIA`；目录所需事实不完整返回 `CATALOG_INCOMPLETE`；只有完整评估后无候选才返回 `NO_MATCH`。 |
| 数据适配 | `ProductCatalogRepository` / 对应现有业务服务读取 Java 产品目录；条款摘要、版本和官方链接必须有来源。 |
| 授权 | 授权上下文 scope：`product.catalog.read`。即使产品信息公开，内部 API 仍要求服务令牌与有效授权上下文。 |

`GET /internal/agent-tools/v1/products/recommendation-criteria?categoryId=...` 返回目录状态（`READY`、`NOT_PUBLISHED`、`NOT_READY`）及 `catalogVersion`、`ruleVersion` 和条件项。条件项分别给出定义允许用途、当前 `activeRuleMode` 和可提交的操作符，避免把仅比较字段误作可筛选条件。当前没有已审核发布数据时，目录状态应明确为未发布/未就绪，Java 不预置演示保险事实。

推荐条件由 Java 发布的类型化条件目录定义，Agent 仅提交已发布代码和匹配该类型的值；不得把开发库字典固化为代码枚举，也不接受任意 map、SQL、自由 URL 或任意排序表达式。条件需区分数据类型、允许操作、硬筛选/偏好/仅比较用途和敏感级别。健康状况筛选属于敏感信息，第一阶段不收集、不传递。现有方案价格可确认是基础价及 `y/d`、`y/m`、`y/y` 单位，最终保费依具体险种及系数规则试算；没有币种字段或通用年预算换算。因此 `annualBudget` 不能直接按基础价硬过滤/报价，后续需调用 Java 专用报价适配；`regionCode` 未确认有权威产品字段前禁用。未知值留空，不让 Agent 猜测。

Java 的 `ProductRecommendationPolicy`/`RecommendationRuleSet` 负责按产品类别声明必填条件、硬性过滤条件、数据完整性要求和偏好排序规则，并输出 `ruleVersion`。候选粒度首期是 `tab_insurance` 的产品，不是 `tab_insurance_plan` 中的方案档位。先执行已批准的硬性排除，再按 Java 版本化且经业务审阅的明确规则排序；不得使用佣金、运营费率或其他与客户利益无关的指标，也不得由 Agent 调整分值。结果最多三项，不静默放宽条件。详细规则、资料版本和缺数语义以 [产品推荐专项计划](product-recommendation-plan.md) 为准。

工具请求示例（只展示已允许的通用字段；分类枚举运行时由 Java 提供）：

```json
{
  "requestId": "uuid",
  "turnId": "uuid",
  "criteria": {
    "categoryId": "java-returned-category-id",
    "items": [
      {"code": "insured_group_type", "operator": "EQ", "valueCode": "GROUP", "customerIntent": "MUST_HAVE"},
      {"code": "covered_activity", "operator": "IN", "valueCodes": ["SPORTS"], "customerIntent": "MUST_HAVE"},
      {"code": "waiting_period_days", "operator": "LTE", "numberValue": 0, "unit": "DAY", "customerIntent": "PREFERENCE"}
    ]
  },
  "limit": 3
}
```

以上仅是协议结构示意，示例属性必须先经 Java 条件目录发布，不能当作当前已有条件。`customerIntent` 描述客户意图；属性定义的 `allowedUses` 和规则项的 `ruleMode` 分别控制可用能力和执行方式。客户的 `MUST_HAVE` 不能把偏好规则升级为硬筛选，`PREFERENCE` 不能被执行为硬排除。`requestId` 是一次只读评估的关联 ID；超时重试须复用相同 ID 和相同请求体。`criteria` 缺失项保持省略/null，不推断；`limit` 为 1–3 的整数，缺省为 3。同一条件重复、非法操作符/类型或超出实现设定的请求规模应由 Java 校验拒绝。首期仅纳入已盘点、已审核的非敏感条件；年龄、预算、地区及健康条件没有专门业务契约前不启用。各产品类别的必填条件由 Java 规则配置返回，不能只写死在提示词中。

响应至少包括 `status`、`evaluationCompleteness`、`missingCriteria`、`unsupportedCriteria`、`unresolvedCriteria`、`excludedProductCount`、`candidates`、`evaluatedAt`、`catalogVersion`、`ruleVersion` 和 `engineVersion`。业务状态为 `MATCHED`、`NEEDS_CLARIFICATION`、`UNSUPPORTED_CRITERIA`、`CATALOG_INCOMPLETE`、`CATALOG_NOT_READY`、`NO_MATCH`，都不代表 HTTP 异常。`CATALOG_NOT_READY` 表示该类别没有可执行的已发布目录；`CATALOG_INCOMPLETE` 携带候选时，`evaluationCompleteness=PARTIAL`，候选只代表已验证子集，前端/Agent 必须明确说明。每个候选包含 `productId`、`name`、`productVersion`、按条件代码列出的 `attributeDefinitionVersions`、`verificationStatus`、`matchReasons`、`coverageHighlights` 和 `importantLimits`。比较事实需为每个可比较属性显式返回 `KNOWN`、`NOT_PROVIDED` 或 `NOT_APPLICABLE`，不允许把缺失字段静默省略。`NO_MATCH` 只在完整评估后返回。没有已核实路由前不返回 `detailUrl`；链接只能来自批准目录/域名。只有准确报价接口接入时才返回含金额、币种、缴费周期及 `premiumAsOf` 的报价对象。Java 规则表示候选匹配，不表示承保、核保或投保保证。完整结果语义见[Java 产品推荐与比较专项计划](product-recommendation-plan.md)。

以上业务状态均由 HTTP 200 返回；参数不合法返回 400/422，未认证返回 401，scope 不足返回 403，系统或依赖不可用返回 5xx。不得把“需要追问”或“资料未维护”包装成通用异常，也不得把资料缺失误报为无匹配。`NO_MATCH` 仅用于相关目录事实完整并已完成评估的情况。

### B. 保险产品与条款问答（第一阶段）

| 层 | Java 侧结构 |
| --- | --- |
| 产品资料接口/同步 | 由产品目录或资料管理功能提供已审核、有效期内的产品和条款版本；不以 Agent 编写内容为数据源。 |
| 应用/业务层 | `ProductKnowledgeService` 或资料发布适配，确认产品 ID、版本、状态和有效日期。 |
| 数据适配 | 导出/同步经过审核的条款资料及来源标识到 Python 知识索引；索引是检索副本，Java/正式条款仍是权威来源。 |
| 响应契约 | Python 返回回答及 `sources[]`；Java 将来源一并返回前端。每个来源包含 `sourceId`、`productId`、`productVersion`、`documentId`、`title`、`section`、可选 `page` 和批准的 `url`。来源必须由检索结果产生，不能由 LLM 编造。 |

若客户问具体保费或在售状态，应通过 Java 产品事实接口读取实时数据，不能只从向量检索内容推断。

#### 资料发布与索引同步契约

Java 产品/文件管理是已审核资料的权威来源；Python 向量索引是派生检索副本。每份资料至少需要 `documentId`、`productId`、`productVersion`、`documentType`、`title`、`sourceUri`、`contentHash`、`status`、`effectiveFrom`、`effectiveTo`、`approvedAt`。状态建议为 `DRAFT`、`PUBLISHED`、`WITHDRAWN`；只有 `PUBLISHED` 且在有效期内的资料可以检索。

发布或撤回必须触发可重试的索引同步。首期可以由 Python 索引任务通过独立的服务身份拉取 Java 发布清单和授权下载地址；该同步身份与客户聊天 grant 分开。同步按 `documentId + productVersion + contentHash` 幂等，撤回/过期资料应从检索索引失效。Java 应提供或明确复用产品资料审核、版本和文件存储能力；如果现有后台没有这些管理能力，先补资料管理流程，不能让客服 Agent 自行上传或发布条款。

产品事实的私有查询可由推荐 API 返回候选事实，或提供 `GET /internal/agent-tools/v1/products/{productId}/facts?version=...`。响应仅包含前端/Agent 需要的已审核字段和来源版本；不把通用 `InsuranceVO` 原样返回。详情 URL 来自 Java 产品目录，并限制为平台批准的路由/域名。

### C. 本人保单查询与解释（第二阶段）

| 层 | Java 侧结构 |
| --- | --- |
| 客户 API | 登录后 `GET /ai-assistant/policies?cursor=...&limit=10` 游标分页；`GET /ai-assistant/policies/{policyId}` 查看选中保单详情。H5 只显示安全白名单字段，并提供“让 AI 介绍”。 |
| 内部工具 API | `GET /internal/agent-tools/v1/policies/summary` 返回有限的本人有效保单摘要；`GET /internal/agent-tools/v1/policies/{policyId}` 在重新验证归属后查询单张保单。选中介绍通过 `selected_policy_id` 传递，Java 校验后由 Agno `get_selected_policy` 拉取。 |
| 契约 DTO / VO | Java `AiOwnPolicyRecordDTO`、`AiOwnPolicyPageResponseVO`、`AiOwnPolicySummaryItemVO`、`AiOwnPolicyDetailResponseVO`；Python `OwnPolicySummaryResponse`、`OwnPolicyDetailResponse`。 |
| 授权 | scope：`policy.own.read`；授权解析得到当前用户，Java 再检查数据归属和字段权限。 |
| 业务层 | Java `AiOwnPolicyQueryService`；Python `OwnPolicyService` 调用 `OwnPolicyJavaClient`，Agno `OwnPolicyTools` 提供 `get_my_policies`、`get_my_policy`。 |
| 数据适配 | Java `AiOwnPolicyRepository` / `AiOwnPolicyMapper` 执行 owner-scoped SQL；禁止 Python 访问保险数据库。 |
| 允许回答字段 | 保单状态、保费与缴费周期、保障起止时间、生效日期、续保信息；有数据源且经批准后再开放其他字段。 |

本人保单摘要的候选字段建议为：`policyId`（不透明内部标识）、`productId`、`productName`、`policyStatus`（Java 枚举）、`premiumAmount`、`currency`、`paymentFrequency`、`coverageStartDate`、`coverageEndDate`、`effectiveAt`、`renewalDate`、`asOf`、`productVersion`。不返回完整保单号、身份证号、银行卡号或健康信息。金额使用 decimal 数值并与 ISO 货币代码配对；日期使用 ISO-8601。无数据返回 `null` 并带数据状态，不让模型推算。多保单时先返回有限摘要列表；后续查询指定 `policyId` 时，Java 必须重新校验该 ID 属于当前 grant 用户。

保单查询响应携带 `asOf`（数据查询时间）和 `productVersion`（保单对应的条款版本）。对等待期、免责、理赔条件等条款问题，应以该保单对应的产品/条款版本检索，不混用当前在售版本。

实现前必须盘点现有状态枚举和字段语义，并建立映射表；尤其不要把保单“保障起始时间”“生效时间”和“等待期截止时间”混为一个字段。现有 `WarrantyVO` 同时包含 `warrantyNo`、投保人身份信息、保费、保障起止、核保/保单状态、等待期及订单等字段，不能直接序列化给 Agent。只从明确白名单 DTO 输出。第二阶段的安全初始范围定为当前登录客户作为投保人、且状态为待付款/待生效/保障中；不含他人代投、家庭关系人和历史/终止保单。若扩展到本人作为被保险人，需先验证关系人 ID 属于当前用户。

本人保单浏览接口只按当前登录人的身份证号匹配其作为投保人的记录，不接收客户端传来的用户身份或关系人 ID；分页和详情都使用 owner-scoped SQL。状态映射依据 `WarrantyConstant` 的 0–10 定义。Agent 的批量摘要仍只列待付款、待生效和保障中的记录；客户可从完整分页中选择历史状态的保单，Java 在聊天请求处再次验证归属，Agent 只能描述工具返回的名称、状态和保障日期。项目中未提供的条款内容不由 Agent 推断。`WarrantyServiceImpl.findById(String)` 没有用户参数，不可直接作为 Agent 工具的授权查询。现有 `doPremium` 属于投保流程及类型处理器，不应直接视为 Agent 可用的报价契约。

### D. 会话与聊天历史

Java 管理会话所有权、状态及客户侧访问校验；Agno PostgreSQL 保存会话消息、运行状态和必要追踪。Java 不重复写整份聊天正文。客户历史接口已实现：`GET /ai-assistant/conversations/{sessionId}/messages?cursor=...&limit=25`。`limit` 表示最多读取的 Agno run 数（每轮最多两条客户可见消息），Java 限定为 1–50；首屏读取最新页面并按时间正序返回，后续用 `nextCursor` 向更早消息翻页。`GET /ai-assistant/conversations?cursor=...&limit=20` 会话列表仍未实现；删除接口为 `DELETE /ai-assistant/conversations/{sessionId}`。

历史接口从客户登录态取得用户 ID，并按 `(owner_user_id, session_id)` 检查 Java 会话状态和留存期限；缺失、过期、待删除、已删除或非本人会话统一返回空历史，避免会话枚举。Java 从自己的 conversation 映射取得内部 Agno session ID，再通过仅供服务间访问的 Python `GET /internal/sessions/{agnoSessionId}/messages?before_run_index=...&limit=...` 读取，并通过 `X-Agno-User-Id` 内部请求头传递 Java 已解析的登录用户 ID，避免把用户标识放进 URL 日志。该 Python 路由要求 `agent.chat` scope，并在 Agno 数据库查询时再次限定该 user ID。Python 只从完成的顶层 run 提取原始用户输入和最终助手答复；Java 再白名单过滤角色、正文长度，并把 Agno run ID 映射成 Java turn ID。客户响应不包含 Agno session/run ID、工具输出、内部 trace、服务令牌或推理内容。

Java 调 Python 的历史接口已实现为 `GET /internal/sessions/{agnoSessionId}/messages?before_run_index=...&limit=...`，使用 `agent.chat` scope；`DELETE /internal/sessions/{agnoSessionId}` 使用独立 `agent.operator` scope。删除要有明确语义：客户侧立即不可见，Python 清除 Agent session/history，Java 标记删除完成或失败并支持重试；重复删除应幂等。当前 Agno PostgreSQL 使用持久卷且未配置自动清理/TTL，因此数据会持续保留，直到人工删除或存储被清空。上线前须由数据/隐私责任方确定会话正文、trace、备份及审计记录的分别留存期限与删除 SLA。

`ai_conversation` 保存客户 ID 与内部 Agent session 的映射及状态；会话状态建议为 `ACTIVE`、`CLOSED`、`DELETE_PENDING`、`DELETED`。`ai_turn` 保存幂等键、轮次状态和 Agno run 关联；轮次状态建议为 `PROCESSING`、`COMPLETED`、`FAILED`。同一 `clientMessageId` 重试时复用原 `turnId`，禁止重复执行模型；如果无法回读已完成结果，明确返回 `PROCESSING` 或可恢复错误，不得静默启动第二个 run。若需要正式销售或服务审计，应新增独立 Java 审计事件与留存规则，不将 Agent trace 视为审计记录。

多轮推荐澄清默认从同一 Agno session 的历史恢复已确认条件；Java 不保存重复的完整聊天内容。Agent 不应将推断值写入长期用户记忆。每轮传给 Java 的推荐请求仍须包含完整、经过校验的当前 criteria，而不能依赖 Java 读取模型历史。

### E. 后续评估：理赔状态与人工客服

当前仓库可以确认存在理赔表单项相关模型/枚举，但没有找到可供客户使用的理赔案件查询与客服工单接口。因此本节只规定边界，真正实现前必须接入权威理赔/客服系统并确认数据来源和状态含义。

| 能力 | Java 接口/层职责建议 | 授权与边界 |
| --- | --- | --- |
| 本人理赔进度只读查询 | `POST /internal/agent-tools/v1/claims/status`；`ClaimQueryRequest`、`ClaimStatusResponse`、`ClaimStatusDTO`；`ClaimQueryService` 读取理赔系统并映射业务状态、更新时间、下一步客服提示。 | `claim.own.read`；不信任请求体中的 userId；按 grant 主体验证案件归属；不返回内部调查备注、他人资料或未经审核的拒赔推断。 |
| 理赔材料/流程说明 | 复用已审核的产品条款/理赔指南资料，返回材料清单来源和版本；动态案件缺项由理赔业务 API 明确提供。 | 静态指南可公开检索；个人案件材料只按本人权限读取；不让 Agent 判断材料是否足以赔付。 |
| 转人工 | 前端用户明确确认后调用客户 API `POST /ai-assistant/conversations/{sessionId}/handoff`；Java `HandoffApplicationService` 创建人工服务记录/工单并返回受理状态。 | Java 客户 API 的登录态和显式确认控制；`support.handoff.create` 是 Java 侧权限能力标识，不签发给 Python/模型。不要仅凭模型工具调用直接创建工单。向人工传递的摘要需标记为 AI 生成并允许坐席回看必要上下文。 |

转人工响应建议包含 `handoffId`、`status`、`queueName`、`createdAt`、`estimatedWait`（仅在有可靠数据时）。状态跟进另由 Java 以 owner-scoped 接口提供。理赔查询及人工客服均为只读查询/服务转接，不扩展为自动提交理赔、改变保单或自动给出赔付结论。

## 通用内部接口安全与错误契约

所有 `/internal/agent-tools/**` 请求必须同时携带 scope 受限的 OAuth2 Bearer token 与 `X-Agent-Authorization-Context`。有请求体的写/命令工具须携带 `turnId`、唯一 `requestId`；只读 GET 工具用受限查询参数且保持幂等。`traceId` 通过 `X-Trace-Id` 传播。Java 校验 JWT 签名、服务 client ID、scope、Redis grant 有效期和 Controller 声明的 scope；个人数据查询从 grant 取用户身份，忽略请求体中的身份字段。当前工具客户端仅允许 `product.catalog.read`；只有第二阶段本人保单功能启用且 Java 授权策略允许后，才增加 `policy.own.read`；理赔只读功能启用后才增加 `claim.own.read`。前端、Python 和模型均不能申请或扩展 scope。转人工由已登录客户明确确认后直接调用 Java 客户 API，不授予模型可独立建单的 scope。Grant 是短时 bearer 凭证，在 TTL 内可供本轮多个受限只读工具调用，不是一次性令牌；需限流并记录工具调用审计。

scope 校验必须由所有内部工具共同使用的授权组件/拦截器强制执行，不能只依赖 Controller 开发者记得手动调用 `require()`。当前首个只读目录切片由 `AgnoInternalApiSecurityConfig` 强制校验服务令牌、授权上下文和 Controller 声明的 scope；Java 仅签发 `product.catalog.read`。保单、报价等新 scope 必须先有独立服务端策略和接口，不能复用或扩大当前 grant。

标识语义统一如下：`clientMessageId` 是前端幂等键；`turnId` 是 Java 为一个用户消息分配的业务轮次 ID，并在跨服务调用中保持不变；`runId` 是 Agno 执行 ID；`traceId` 是跨 Java/Python 的链路追踪 ID；`requestId` 是某次内部工具调用 ID。它们不可互相替代。

同一 owner、会话和 `clientMessageId` 的重试不得再次执行 Agent run；应返回原 `turnId` 与已完成结果，或明确返回处理中状态。Java 在 `ai_turn` 上保留唯一约束和状态，Python/Agno 按 `turnId` 保证运行幂等或提供结果回读。若当前阶段暂不实现结果回读，客户端和 Java 不得对超时聊天自动重试，应明确提示用户查看会话状态。只读业务工具可以重试，但每次仍须重新校验授权上下文。

HTTP 错误响应统一包含 `code`、安全可公开的 `message`、`traceId`。传输/授权错误至少区分 `UNAUTHORIZED`、`SCOPE_DENIED`、`VALIDATION_ERROR`、`NOT_FOUND`、`RATE_LIMITED`、`UPSTREAM_UNAVAILABLE`；`NEEDS_CLARIFICATION`、`UNSUPPORTED_CRITERIA`、`CATALOG_INCOMPLETE`、`CATALOG_NOT_READY`、`NO_MATCH` 仅作为 HTTP 200 业务状态，不作为 HTTP 错误码。跨用户的保单不存在与无权访问应采用不泄露记录是否存在的响应策略。

## 实施顺序与现状

### 本人保单摘要查询切片

已实现只读内部接口 `GET /internal/agent-tools/v1/policies/summary`、`GET /internal/agent-tools/v1/policies/{policyId}` 和 Agno 工具 `get_my_policies`、`get_my_policy`。Java 从短时授权 grant 取得当前用户 ID，并在每次数据库查询中将该用户的有效本人关系记录与保单投保人身份进行匹配；请求不接受 user ID、保单号或关系人 ID。列表返回当前用户作为投保人、状态为待付款/待生效/保障中的最多 20 条摘要，包含内部 policyId、产品名、状态、保障起止时间和自动延保状态；超过 20 条会带截断标记。详情只接受列表返回的数字 policyId，并再次校验保单归属；不存在与不属于当前用户统一返回 `NOT_FOUND`。不返回身份证号、完整保单号、投保快照、订单、健康信息或历史/终止保单。

内部工具结果中的 `policyId` 只在 Java 与 Python 的内部契约中传递，客户聊天 DTO 使用不含此字段的独立 VO；Java Mapper 在跨服务响应处校验状态和字段白名单后再组装客户响应。

内部接口要求 `policy.own.read` OAuth scope 与同名短时 grant scope。部署时须将该 scope 加入 Agent 工具 OAuth 客户端的授权范围；缺少任一授权时接口拒绝访问。当前 Agno 会话/trace 的留存配置仍须按数据留存策略管理，不应将本功能视为已完成个人数据留存验收。

1. 盘点现有产品、条款文件、保单/缴费数据、客户关系和理赔数据模型；明确“本人”与产品字段语义。
2. 冻结 DTO/OpenAPI：确定推荐 criteria、候选卡片/来源字段、业务状态、错误码、scope 和 ID 语义。
3. Java 实现 AI 专用类型化产品查询、授权强制校验和产品/条款发布同步；不把通用查询对象或模型原文直接传给 Mapper。
4. Python 按契约注册推荐工具；缺字段时追问，Java 候选和引用以结构化数据透传。
5. 前端展示推荐卡片/来源；补会话目录、分页历史、幂等与删除链路。
6. 再实现 `policy.own.read`，使用 owner-scoped 查询和条款版本映射完成本人保单问答。
7. 理赔查询和人工转接作为后续独立范围；先确认权威案件系统、人工客服接口与审批/确认流程，再定义 OpenAPI。

当前已有客户聊天入口、Java 到 Python HTTP 调用、短时 Redis 授权上下文和内部工具路径保护。Java 目录查询切片包括动态分类列表、按分类查询最多三项上架产品、白名单 DTO 映射和 `product.catalog.read` scope 强制校验；Python Agent 已调用这两个只读端点，并将 Java 校验过的候选作为聊天响应的 `recommendations` 返回。它只支持分类目录检索，不是完整个性化推荐，前端卡片尚未完成。AI DTO、会话/轮次/清理任务 DO、owner 范围 Mapper、事务服务和清理 outbox/调度骨架已加入；V01、V02 表结构已在本机开发库执行。AgentOS 删除接口适配尚未实现，未配置适配器时 worker 不领取任务；因此该骨架不等于已完成清理，也不能据此启用持久化。当前还未将新持久化服务接入客户聊天 Controller/Agno 调用链。产品推荐规则、报价、保单查询和理赔工具仍未完成。条款来源管理与 Python 索引/检索代码已加入，包含结构化 citation Java 映射；但数据库迁移、OAuth/embedding 配置、端到端验证和前端引用展示仍待处理。开发数据库分类含测试项；健康筛选不暴露给第一阶段 Agent。价格币种及通用年预算换算未定义，地区字段也未确认。Agno PostgreSQL 使用持久卷，Python 侧自动清理/TTL 尚未实现；启用真实会话写入前必须落实 Java 和 Python 两侧删除策略。本文其余建议路径和字段仍需在对应切片实施前作为 OpenAPI/DTO 冻结。

当前分类目录切片已将至多三条 Java 候选通过聊天响应的 `recommendations` 返回。Python 客户端 `app.clients.java_internal_api.JavaInternalApiClient` 已增加只允许访问推荐 POST 路径的 `post_json`；`app.schemas.product_recommendation` 和 `app.services.product_recommendation.ProductRecommendationService` 负责条件目录读取、请求校验、Java 推荐调用及响应校验。这只是客户端/用例层，尚未注册 Agno 工具，也未接入聊天结构化响应。后续仍需把工具结果采集、澄清交互和前端结果卡片接通；会话能力还需完成留存、删除语义和幂等结果回读。

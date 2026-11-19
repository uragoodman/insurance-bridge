# 客服缺口对应的分层设计

**状态：实施设计。**本文把客服用户流程中的缺口映射到 Java 和 Python 的代码层，并区分已存在的层、待扩展的层与外部前置条件。没有权威数据源或业务系统的功能只定义接入边界，不创建空 Mapper 或假接口。

## 分层约定

Java 继续使用现有 `insurance-app` 和 `insurance-service` 模块：

| 层 | 类型与职责 | 约束 |
|---|---|---|
| Controller | `insurance-app/web/ai/internal` 接受 Python 工具请求；`AiAssistantController` 接受客户聊天 | 不访问 Mapper；内部端点校验 OAuth scope 与聊天授权上下文 |
| DTO / VO | `insurance-app/dto/ai`：请求 DTO、响应 VO、跨服务契约 | Python 请求/响应对象与数据库对象分离；响应只含回答所需字段 |
| Service | 应用用例编排及领域校验 | Controller 不含业务判断；Java 对保险事实、匹配和本人数据归属负责 |
| Repository / Mapper | `insurance-service/repository/ai`、`mapper/ai` 和 `pojo/ai` | 只有 Java 自有数据库查询才建 Mapper；SQL 必须显式带状态及归属条件 |
| Client | Java 调 Agno、外部理赔/客服系统的 HTTP 适配器 | 外部系统调用使用明确的客户端身份和白名单路径；不在 Mapper 中访问外部服务 |

Python 继续按 `app/schemas/`、`app/clients/`、`app/services/`、`app/tools/` 分层。Pydantic schema 负责协议校验；Client 只传输；Service 执行用例规则并校验 Java 返回；Toolkit 将能力暴露给 Agno。Python 不直接查询 Java 保险库表，也不决定产品是否匹配。

本项目的“Bean”按具体职责命名：持久化行对象使用 `Row`/POJO，服务间输入使用 `RequestDTO`，服务间输出使用 `VO`；不让一个通用 Bean 同时承担数据库、HTTP 与模型上下文协议。

## 功能到代码层映射

| 功能 | Java 持久化与 Bean | Java Service / Controller / Client | Python Schema / Client / Service / Tool | 状态与前置条件 |
|---|---|---|---|---|
| 产品分类和目录查询 | 复用 `CategoryMapper`、`InsuranceMapper`、`AiProductCatalogRepository`；使用目录 VO | `AiProductCatalogService`、`AiProductCatalogController`；Python 是调用方 | `ProductCategory`、`ProductCatalogSearchResponse`；`JavaInternalApiClient`、`ProductCatalogService`、`ProductCatalogTools` | **已实现并验证。**限制为动态分类与最多三项在售目录结果，不代表个性化推荐或条款问答 |
| 个性化推荐 | 复用 `AiRecommendationMapper`、`AiRecommendationRepository`、`AiRecommendation*Row` 和现有 Flyway 模型；复用推荐 DTO/VO | `AiProductRecommendationService`、`AiProductRecommendationController` | `ProductRecommendation*` schemas、`ProductRecommendationService`、`ProductRecommendationTools`；Java Client 的 POST 路径白名单 | **代码链路已具备；真实推荐需经业务审核发布的条件、事实和规则。**本地开发 seed 不能作为上线数据 |
| 产品对比与详情链接 | 扩展已存在候选/事实 VO，明确每个对比字段的 `KNOWN`、`NOT_PROVIDED`、`NOT_APPLICABLE`；链接只来自审核目录 | 由推荐/目录 Service 组合权威字段；Controller 返回结构化候选及允许域名内的详情引用 | 增加比较结果 schema 和结果收集；Service 校验候选、比较维度和链接；Tool 调用 Java 后把结构化事实传给聊天响应 | 不另造一套平行 Mapper。先确定正式比较字段、来源和产品详情路由；缺字段必须显式返回，模型不得补造 |
| 产品条款与保障问答 | Java 产品/文件管理需提供审核状态、版本、有效期、撤回状态和来源元数据；仅当元数据由本地权威库持有时建对应 Mapper/Row | Java 发布清单/版本 Service；若资料由独立文件服务管理，使用 `ProductDocumentClient`，不复制为第二套事实库 | `KnowledgeQuery`、`KnowledgeSource` schemas；Java 文档清单/下载 Client；Python 知识索引 Service、来源校验和 `ProductTermsTools` | 当前缺审核发布与索引同步流程。发布、版本失效和撤回必须可同步到检索索引，回答需携带来源 |
| 通用保险知识 | 不为模型常识建立保险业务 Mapper | 如业务知识归 Java 管理，则由发布 Service 提供版本化资料；否则 Java 不伪造数据端点 | 与条款知识共用经过批准的检索基础设施；单独区分通用知识 source type | 没有经批准知识源前，只能处理低风险一般说明；产品条款、医疗、法律和承保判断不得由自由生成替代 |
| 本人保单 | 在确认账户与投保人归属后建立 owner-scoped `OwnPolicyMapper`、最小 `OwnPolicyRow`；必要时先补可信身份绑定与索引 | `OwnPolicyQueryService`、`OwnPolicySummaryRequestDTO/ResponseVO`、内部保单 Controller；新增 `policy.own.read` scope。不得直接开放旧 `findById` | `OwnPolicySummary` schema；Java Client、`OwnPolicyService`、`OwnPolicyTools` | **阻塞于身份归属模型。**请求不接受 userId、身份证号或原始保单号；每次 SQL 同时核验 grant 用户、保单与有效状态 |
| 理赔进度 | 先确认理赔权威系统及数据所有者；若理赔库属于 Java，才建 owner-scoped Mapper/Row | 如果是外部理赔系统，用 `ClaimsClient`、`ClaimsQueryService` 和只读内部 Controller；配置专属 `claims.read` scope | `ClaimStatusQuery/ClaimStatus` schemas、Java Client、Service 和 `ClaimsTools` | 当前未确认案件/进度权威系统。理赔流程知识可由审核知识库提供，不能与个人进度查询混为一项 |
| 报价/核保/投保 | 现有投保试算路径不是 AI 通用报价契约；不允许直接暴露数据库/投保 Mapper | 只有业务确认正式报价 API、金额语义、币种、时效与资格边界后，才设计 Java `QuoteClient/QuoteService`；不提供 AI 核保或自动投保 Controller | 未批准前不注册报价/投保工具；批准后新增严格的报价 schema、Client、Service 与只读 Tool | **当前关闭。**推荐候选不是报价、承保结论或购买资格；投保、支付、退保等写操作不属于客服 Agent |
| 投保/售后流程说明 | 不需要新建业务 Mapper，除非业务流程内容由 Java 后台管理 | 官方流程与链接由既有入口配置；由 Java 返回经过批准的路由，或由审核知识库维护 | `BusinessGuide` schema、知识检索 Service；只有真实安全的动态入口才封装 Client/Tool | 可先回答静态已审核流程并给官方入口；不把流程咨询解释为授权办理 |
| 人工客服转接 | 不建客服 Mapper，除非工单系统由本服务拥有 | 明确客服平台后新增 `CustomerSupportClient`、`HandoffService`、请求/结果 DTO 和受 scope 保护的内部端点 | `HandoffRequest/Result` schema、Client、Service、明确的转接 Tool；只传允许共享的摘要 | 当前无转接系统/权威联系入口。不能伪称已转接；需明确队列、可用时间、失败处理与用户确认 |

## 意图识别与追问属于哪一层

意图路由不需要 Java Mapper。由 Python Agent 编排层负责识别当前任务、提取用户已提供的槽位、判断需要调用哪个 Tool，或向用户追问。可增加 `CustomerIntent`、`ExtractedNeed`、`MissingInformation` 等结构化 schema 和一个 Python `ConversationFlowService`，但 Java 仍校验所有工具参数和权限。该层不得自行声称数据查询成功，也不得把模型置信度当作业务事实。

当一条消息同时包含“比较产品”和“查询生效时间”等意图时，Python 应复用对话中已知的产品与偏好，按所需权威数据调用对应工具；缺少关键产品/条件时先追问。工具失败、未发布目录、条款缺失、授权失败和用户超范围请求都必须映射为明确、可理解的对话结果。

## 建议实施顺序

1. **补齐推荐/比较可用数据**：产品事实与比较字段审核发布、来源版本、官方详情引用；为发布的真实目录做 Java 到 Python 端到端验收。
2. **建立条款发布与检索链路**：明确资料权威存储、审签角色、版本有效期、撤回事件和来源引用，再接入 Python 检索工具。
3. **落本人保单只读切片**：先证明用户到投保人的可靠绑定，定义最小字段和 SQL 归属条件，再建 Mapper、Service、Controller、scope 与 Python 工具。
4. **确认外部系统后再接理赔/转人工**：未确认权威系统前只保留受控的解释和入口，不搭建空 Client 或成功假响应。
5. **报价/投保单独评审**：未冻结正式报价、核保和写操作边界前，保持 Agent 无相应工具。

本文是分层设计和实现顺序，不表示规划中的 Java/Python 类、接口或业务数据已经全部落地。现状以 [Agent 功能范围与当前状态](agent-capabilities.md) 和对应专项文档为准。

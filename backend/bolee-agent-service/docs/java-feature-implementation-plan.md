# Java AI 功能实施计划

## 目标与边界

本文规划两项待开发能力：个性化产品推荐/比较，以及登录客户查询本人保单。采用“Python 理解对话、Java 决定业务事实和权限”的模式。Python 不连保险数据库；Agent 不做核保、报价推算、承保或保单变更。当前只读目录工具是基线，不能复用为完整推荐结论。

这种边界符合成熟平台常见做法：Salesforce 的产品推荐 API 将候选、推荐理由和兼容性结果建立在业务规则之上；Guidewire 将保单搜索和读取建模为有类型的 API。借鉴的是 API 与业务责任划分，不代表本项目已接入这些厂商产品。见 [Salesforce Product Recommendations](https://developer.salesforce.com/docs/revenue/revenue-management/guide/connect-resources-product-recommendations.html)、[Guidewire PolicyCenter policy search](https://docs.guidewire.com/cloud/pc/202603/apiref/generated/Example%20Policy%20for%20PA%20API/search-policies--post/) 和 [Microsoft API least privilege](https://learn.microsoft.com/en-us/security/zero-trust/develop/protect-api)。

## 四项审查问题的耦合关系

这不是一个大功能里四个可以分别修补的小点，而是两个纵向切片：

1. **产品推荐切片**：审核发布的数据源 → Java 规则筛选/比较 → 推荐 API。数据源没有审核和稳定字段前，不能只先写推荐 Mapper。
2. **本人保单切片**：账户与保单的可信归属 → `policyRef` 定位 → owner-scoped Mapper → 索引/迁移 → 最小摘要 API。归属、引用和索引必须按最终 SQL 一起设计。

两条切片共享 MySQL/Flyway 和内部 API 安全框架，但业务模型独立；可以分开迁移和发布，不能把产品标签表与保单授权模型耦合在一起。

## 产品推荐：数据源与治理模型

### 当前库能做什么、不能做什么

当前 `tab_category_condition` 定义分类筛选项，`tab_insurance_condition` 保存产品的条件值；`tab_insurance` 还含 `labels_json`、`remake_json`、`comment`、`remake` 等字段。这些是可盘点、可迁移的来源，不是现成的 AI 规则目录：条件值使用动态 key/JSON，缺少统一类型、敏感级别、可筛选标志、审核版本及撤回语义。`insuranceState=0` 只是上架状态。首页 `recommend-insure-list` 按展示字段取数，也不是按客户偏好匹配的推荐服务。

### Java 数据管理机制

当前分类条件表和产品条件表只作**盘点/迁移来源**。目标是通用的条件定义（带定义版本和允许用途）、产品事实版本、规则集/规则项及不可变目录快照；单次推荐固定读取同一快照中的产品、条件定义和规则版本。逻辑模型、发布语义及 Flyway 设计边界以[Java 产品推荐与比较专项计划](product-recommendation-plan.md)为准，本节不另行定义一套简化数据结构。

Java 后台维护并审核这些字段，Agno 只读。现有条件值导入后只进入草稿，由产品负责人审核并归一化为稳定代码。推荐查询只使用快照中已发布且有效的产品事实；`data_state=0`、`insurance_state=0` 是必要条件，但单独不足以证明 AI 资料已审核。没有结构化事实或未审核的产品不进入已验证候选。

推荐请求中的 `customerIntent`（客户说“必须”还是“偏好”）与规则 `ruleMode`（硬筛选/偏好打分/仅比较）分开定义：偏好不能硬排除，客户意图也不能越过条件目录授权。规则按“硬性排除 → 偏好排序 → 稳定 tie-break”执行并返回规则/执行器版本，不按佣金或运营费率排序。产品事实缺失需标为部分评估，不能误报 `NO_MATCH`；条款责任、免责和报价继续等待权威来源，不从标签推导。

## 本人保单：归属、引用与数据库查询

### 归属模型

本地开发库的 `tab_warranty` 只有 `applicant_identity_card`，没有客户账户 ID；`tab_customer_relation` 才有 `customer_id` 和 `identity_card`。但仓库现有 `id-card` 接口把请求里的身份证号写入客户信息，`face-check` 路径也不能单凭方法名证明身份服务端已完成核验。因此，不能把当前客户关系里的身份证号直接当作已验证的保单所有权凭证。

`tab_warranty.create_by` 虽然由 `AutoMetaObjectHandler` 从当前登录态填充，但它是通用审计“创建人”，代理人、后台任务或代办流程都可能是创建人，不能当作保单所有人。新签发流程应由 Java 从登录态写入专用 `applicant_customer_id`（不可由请求或 Agent 指定），且仅在被校验的投保人确实是当前账户的本人记录时绑定；与保单在同一事务中持久化。写入前需确认当前账户只有一条有效 `SELF` 关系，避免现有 `buildApplicant()` 未限定有效状态/多条记录时选错申请人。AI 只查询该账号直接绑定且本人为投保人的记录。已有保单该字段为空时不按未核验的身份证号或 `create_by` 自动回填；历史数据只有在可信身份核验或保险权威系统映射确认后才补绑。补绑前这些历史保单不通过 Agent 暴露。该选择避免把用户可编辑的个人资料字段或通用审计字段当授权依据。

### `policyRef` 与索引

`policyRef` 是定位符，不是权限凭证。建议采用带 key version 的 HMAC 签名不透明引用，载荷只含保单主键及 owner account ID；Java 解码后仍必须在同一数据库查询中带上 `applicant_customer_id = 当前 grant 用户`、`data_state` 和允许状态条件。不匹配、伪造和不存在统一返回无记录；密钥放部署密钥管理，不写入仓库。批量摘要列表的首期也可仅返回已过滤事实；只有需要对某一项继续查询时才返回 `policyRef`。

最终 owner 查询确定后，在 Flyway 为 `tab_warranty(applicant_customer_id, data_state, warranty_state, id)` 建组合索引；若账户解析仍需读取 `tab_customer_relation`，则为 `(customer_id, relation, data_state)` 建索引。产品切片应按最终过滤和排序 SQL 为产品版本/属性查询加索引。当前本地表只有主键索引，不能依赖全表扫描作为生产方案。迁移前确认 MySQL 5.7 兼容性、索引长度/重复索引，并在目标库备份后执行。

## 功能一：个性化产品推荐与比较

产品推荐的数据治理、API 和分阶段验收详见[Java 产品推荐与比较专项计划](product-recommendation-plan.md)。

### 调用链

```text
客户 → Java 客户聊天 API → Python Agent 澄清并生成受限 criteria
     → Java 内部推荐 API → Java 校验/查询/规则排序
     → Python 解释 Java 结果 → Java 校验并返回结构化候选
```

Python 负责从自然语言中识别客户明确表达的偏好，并对缺失或含糊的条件追问。它只能提交协议定义的字段和 Java 提供的代码值，不得提交 SQL、自由过滤表达式、健康史或生成的链接。Java 校验每个条件是否属于该产品类别已发布的非敏感筛选项；无法映射到可靠产品事实的偏好标记为不支持或待澄清，不静默猜测。

### Java 分层与契约

| 层 | 规划职责 |
| --- | --- |
| 内部 API | 新增 `POST /internal/agent-tools/v1/products/recommendations`，沿用服务令牌、短时授权 grant 和 `product.catalog.read` scope。 |
| DTO | `ProductRecommendationRequestDTO` 含 `requestId`、`turnId`、`criteria`、`limit`；criteria 仅接受已发布的 `categoryId` 和经确认的非敏感条件代码。禁止 `userId`、健康状况、任意 map 和 URL。响应含业务 `status`、完整度、未评估条件/排除数量、最多 3 个 `tab_insurance` 产品候选及目录/产品/属性定义/规则/执行器版本。 |
| 应用服务 | 校验协议、调用产品推荐业务服务，统一 `MATCHED` / `NEEDS_CLARIFICATION` / `UNSUPPORTED_CRITERIA` / `CATALOG_INCOMPLETE` / `NO_MATCH` 结果；这些是正常业务响应。 |
| 业务服务 | 硬性排除非在售产品，再执行已批准的确定性匹配和排序规则；输出结构化匹配理由、保障亮点和限制。规则版本随发布更新。Agent 不参与资格判断或打分。 |
| 数据适配 | 从 Java 产品目录和当前筛选字典读取白名单字段；不得把通用 `InsuranceVO`、原始 `insuranceJson` 或 `JSON_CONTAINS` 查询直接暴露给模型。 |

### 开发前置条件与首版范围

1. 产品目录数据有测试分类，需增加 AI 可见/发布控制，或配置经业务确认的分类白名单；“上架”不能单独证明资料已审核或适合推荐。
2. 先盘点每类产品可用的非敏感条件，将公开筛选字典映射为稳定代码；健康类条件继续排除。只有映射到可靠数据的字段才可作为硬筛选。
3. 首版按类别和已确认的保障偏好匹配产品，返回最多三项及 Java 生成的匹配理由。条件不足时澄清；目录事实不完整时返回 `CATALOG_INCOMPLETE`；仅在完整评估后返回 `NO_MATCH`，不自动放宽条件。详细数据模型和规则定义见 [产品推荐专项计划](product-recommendation-plan.md)。
4. 比较只使用 Java 返回的相同字段做逐项对比。保费、等待期/免责条款、承保资格、产品版本和详情页链接，只有权威字段、语义及来源确认后才能加入卡片；当前不要调用投保 `doPremium` 作为通用报价接口，也不要生成 URL。

## 功能二：本人保单摘要查询

### 调用链

```text
客户询问 → Java 登录态及会话校验 → Python Agent 调用保单摘要工具
         → Java grant 解析用户并按投保人归属查询 → 白名单 DTO
         → Python 基于返回事实作答 → Java 返回客户
```

Python 请求不包含用户 ID、身份证号或授权 scope。Java 从 Redis grant 取得当前用户，并通过 `applicant_customer_id` 直接过滤保单归属；所有数据归属过滤在 Java 查询中执行。不得直接复用 `WarrantyServiceImpl.myWarrantyVOs` 的关系人分支或无 owner 校验的 `findById`。这一直接账户绑定仅对新写入及可信补绑记录成立；没有绑定的历史数据不开放给 Agent。

### Java 分层与契约

| 层 | 规划职责 |
| --- | --- |
| 授权 | 新增独立 `policy.own.read` scope；聊天 grant 仅在该能力启用时签发。内部 Controller 由现有拦截器强制校验 scope，不能由 Python 或模型申请 scope。 |
| 内部 API | `POST /internal/agent-tools/v1/policies/summary`。请求只接受可选的不透明 `policyRef` 和 `requestId`；不接受 `userId`、保单号、身份证号或原始搜索条件。 |
| Mapper/Repository | 新建 owner-scoped 查询，按 Java grant 用户 ID、有效数据状态及允许状态查询；只选择工具回答需要的列。按单保单查询时在同一 SQL 条件中同时校验解出的保单 ID、owner 和状态。无权与不存在返回相同的对外结果。 |
| 业务服务 | `OwnPolicyQueryService` 映射 Java 状态枚举和字段语义，过滤敏感字段，生成 `asOf` 与最小摘要；模型不推算状态、期限或金额。 |
| DTO | `OwnPolicySummaryRequestDTO`、`OwnPolicySummaryResponseDTO`、`PolicySummaryDTO`。首版只含不透明引用、产品名、已核实的保单状态、保障起止时间及经字段核对可准确解释的保费/缴费周期。 |

首版范围沿用盘点结论：当前登录客户作为**投保人**，保单状态为待付款、待生效、保障中；不包含家庭关系人、他人为客户投保的保单、历史/终止状态。状态映射以 `WarrantyConstant` 为准。特别核实 `premium` 与 `premiums`、`periodicUnit` 的业务含义，以及 `safeguardStartTime`、`waitTime`、实际生效时间的差异；含义未确认的字段不进入响应。币种、续保日期和产品条款版本当前没有权威来源，不虚构。

Java 的结果应作为 Python 工具结果事实来源；Agent 只能解释和组织语言，不能扩大保单范围或补造字段。保单条款解释需等待产品资料审批/版本与索引链路建成，不能以当前在售条款替代客户保单版本。

## 实施顺序与验收条件

1. 产品切片先冻结属性字典、敏感级别、审核/发布流程和比较字段；再建立版本快照、后台审批与 Flyway 索引。
2. 保单切片先让新签发路径持久化 `applicant_customer_id`，定义历史保单可信补绑来源、`policyRef` 签名格式和 key version；再建立 owner 查询和相应索引迁移。
3. Java 分别实现推荐与本人保单应用服务、白名单 DTO、内部 API 和独立 scope。对未发布资料、未绑定保单、伪造引用及跨账户查询采取拒绝默认。
4. Java 用目标 MySQL 5.7 数据库验证 Flyway 迁移和执行计划；通过后再接 Python 类型化客户端、工具注册及聊天结果映射。
5. 端到端验收包括发布/撤回后可见性、澄清/匹配/无匹配、KYC/归属失败、伪造引用、多个本人保单、重复身份记录、索引执行计划及 Java/Python 超时。推荐候选不构成购买承诺，保单查询不构成核保或理赔结论。

推荐功能可先交付，不依赖个人资料；本人保单功能应在 Java owner-scoped 查询和 `policy.own.read` 安全验证完成后开放。条款、报价、理赔和转人工仍是独立后续工作。

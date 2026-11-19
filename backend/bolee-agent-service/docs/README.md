# AI 客服文档索引

本目录按“当前实现、总体设计、专项计划、数据契约”组织 AI 客服资料。**文档中的计划和契约不代表对应功能已经落地**；判断当前行为以 当前仓库 Java/Python 代码和下面的实现状态文档为准。

## 从这里开始

1. [Agent 功能范围与当前状态](agent-capabilities.md)：产品、保单、条款、理赔和转人工的目标范围，以及已经打通的 Java/Python 链路。
2. [客服用户任务流程草案](customer-service-user-flows.md)：各类用户意图、所需信息、追问和转人工条件，以及产品对比的聊天体验。
3. [系统架构](architecture.md)：当前 Java、Agno、PostgreSQL 和内部 HTTP 的实际边界与安全方式。
4. [OAuth2 服务认证配置](oauth2-service-auth.md)：双向 client-credentials 客户端、scope 和部署配置。
5. [已实现的 Java 产品目录切片](java-ai-implementation-slice.md)：分类目录工具现有接口、数据边界和 Python 调用结果。
6. [Java AI 功能总实施计划](java-feature-implementation-plan.md)：产品推荐和本人保单两条 Java 纵向切片及其依赖关系。

## 功能与实施计划

- [Java 产品推荐与比较专项计划](product-recommendation-plan.md)：现有产品表盘点、成熟平台做法、产品条件维护/审核发布、Java 匹配规则、API、索引和分阶段验收。
- [产品比较试点资料核对](product-comparison-pilot-audit.md)：两个开发库候选产品与官网备案版本的核对、可确认事实和发布前置条件。
- [客服缺口对应的分层设计](customer-service-layer-design.md)：将客户意图对应到 Java Mapper/Bean/Service/Controller 和 Python Schema/Client/Service/Tool，区分现有代码、待补层与业务系统前置条件。
- [Java AI 客服结构与接口契约](java-ai-contracts.md)：Java/Python/前端契约、授权 scope、功能分层与各阶段 API 蓝图。
- [AI 系统分层规范](system-layering.md)：Java、Python、数据存储的依赖方向和所有权原则。

## 数据与服务协议

- [Java AI 客服数据模型](java-ai-data-model.md)：Java 会话/轮次持久化 DTO/DO、Flyway 和留存设计。
- [项目数据与业务能力盘点](data-inventory.md)：当前保险 Java 数据来源、字段语义、风险和待确认项；本地数据库仅为开发快照。
- [Python 数据与 Java Client 契约](python-data-contracts.md)：Python Pydantic 协议、Java 内部 Client 和工具调用边界。

## 阅读规则

- 讨论“已经能不能用”时，以当前代码及[功能状态](agent-capabilities.md)为准；接口蓝图和专项计划只描述目标。
- 讨论产品字段是否可用于推荐时，以 Java 产品权威数据和已审核发布版本为准；不要把开发库样例、自由文案或模型输出当业务规则。
- 若文档状态与代码不一致，先修正文档索引所列状态页，再修改契约或实施计划，避免多个文档重复维护同一份现状。

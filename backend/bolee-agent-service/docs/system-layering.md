# AI 客服系统分层规范

## 目的与原则

本规范约束保险 AI 客服后续新增的 Java、Python 和数据能力。采用分层单体加独立 Agent 微服务：边界清晰、接口调用、数据由所属服务管理。暂不把每个功能拆成更多微服务。

关键原则：前端只访问 Java；Java 是客户身份、保险业务和授权的唯一权威；Python 负责对话理解与 Agent 编排，不直连保险业务库；Agno 数据库只服务 Agent 运行时。服务间通过有版本的 API 通信，不共享业务表。此类数据所有权和 API 边界符合成熟微服务的常见设计原则。[Microsoft 微服务数据设计](https://learn.microsoft.com/en-us/azure/architecture/microservices/design/data-considerations)

## 总体调用链

```text
浏览器/客户端
    │ 客户 API、登录态
    ▼
Java insurance-app
    ├─ Web/API 层：聊天、会话、内部 Agent 工具接口
    ├─ 应用层：聊天用例、授权上下文、会话用例
    ├─ 业务层：产品筛选、保单查询、数据权限
    └─ 基础设施层：Agno HTTP 客户端、Redis、Mapper/Repository
          │ 私有 HTTP，服务令牌 + 短时授权上下文
          ▼
Python Agent Service
    ├─ API 层：FastAPI 路由、请求校验
    ├─ 应用层：对话用例、Agent 执行编排
    ├─ Agent 层：Agno Agent、提示词、受限工具
    └─ 基础设施层：Java API 客户端、Agno PostgreSQL、追踪
          │ 仅调用已定义的内部工具 API
          └──────────────────────────────► Java
```

同步聊天先使用 HTTP；消息队列不参与单轮问答。只有文档批量入库、离线评估等耗时后台任务出现后，再考虑用消息队列异步处理。

## Java 分层职责

结合现有 `insurance-app`、`insurance-service` 模块，在 AI 新功能中按以下职责放置代码；不要求一次性重构旧模块。

| 层 | 位置建议 | 负责内容 |
| --- | --- | --- |
| API / 展示层 | `insurance-app/.../web/ai`、`dto/ai` | 请求响应 DTO、参数校验、获取登录用户、调用应用服务；不写 SQL、不拼提示词。 |
| 应用层 | `insurance-app/.../service/ai` | 编排聊天、创建会话、签发授权、调用 Agent 客户端；不承载产品适配规则。 |
| 业务层 | `insurance-service/.../service/ai` | 产品筛选与排序、保单权限校验、产品/保单事实查询；业务规则由 Java 执行。 |
| 基础设施层 | `insurance-app` 的 Agent/Redis adapter；`insurance-service/.../mapper` | Python HTTP 客户端、Redis 授权存取、业务数据库 Mapper 和外部服务适配。 |

新代码的依赖方向应由 API 到应用、由应用到业务接口，再由基础设施实现接口；业务规则不能依赖 FastAPI/Agno、HTTP 控制器或具体数据库客户端。内部 Agent 工具接口仍由 Java 验证服务令牌、解析授权上下文、检查 scope 和数据归属。

## Python 分层职责

目标目录结构如下，新增能力时逐步迁移，避免让 `app/main.py` 持续承载路由、编排和业务细节：

```text
bolee-agent-service/
  app/
    api/                 # FastAPI routers、Pydantic schemas、认证依赖
    application/         # chat、recommendation、policy_qa 等用例
    agents/              # Agno Agent 定义、提示词、工具注册
    domain/              # Agent 侧会话/意图等模型；不复制保险业务规则
    infrastructure/
      java_client/       # 调用 Java 内部业务 API 的类型化客户端
      persistence/       # AgentOS PostgreSQL 会话/运行/追踪存储
      observability/     # 日志、指标、追踪适配
```

API 层做协议和输入校验；应用层负责一次对话的执行流程；Agent 层只暴露窄权限、明确输入输出的工具；基础设施层封装 Java HTTP、Agno 存储和监控。FastAPI 路由按功能拆分为多个 `APIRouter`，由应用入口组合，这是 FastAPI 官方支持的多文件组织方式。[FastAPI 多文件应用](https://fastapi.tiangolo.com/tutorial/bigger-applications/)

Python 不能执行任意 SQL、任意 URL 请求或任意 Java 方法调用。每个工具对应一个清晰的 Java API，例如“搜索公开产品”或“查询本人保单摘要”；模型生成的客户 ID、保单 ID 和过滤条件均视为不可信输入。

## 数据所有权

| 数据 | 权威存储 | 约束 |
| --- | --- | --- |
| 产品目录、保单及业务交易 | Java 业务数据库 | Python 不直连、不读写业务表。 |
| Agent 会话消息、运行状态、必要追踪 | Agno PostgreSQL | 仅 Agent 服务访问；经 Java 验权后向客户提供会话相关能力。 |
| 短时授权上下文、临时缓存 | Java Redis | 授权短时有效、绑定用户和会话；不作业务事实或长期记录。 |
| 产品条款知识索引 | Agent 知识存储（如启用） | 仅保存已审核、版本化的资料和来源标识；它是检索副本，不是条款权威源。 |

聊天会话的用户归属、状态和访问控制由 Java 管理；完整 Agent 历史保存在 Agno 存储，避免两边双写聊天正文。后续若要求形成正式销售或服务审计档案，应另行定义 Java 侧审计事件和留存规则，不把模型 trace 当作业务审计凭证。

## 服务契约与横切要求

- 客户 API 与内部 Agent API 分开；Python、Agno UI 和数据库均不得暴露给浏览器。
- API 使用稳定字段、显式版本、`requestId`/`traceId`；为超时、错误、重试和重复请求定义行为。
- 每次个人数据读取均在 Java 做用户归属、scope、字段最小化检查；模型提示词不是授权机制。
- 日志记录调用方、会话、工具、耗时、结果状态和规则/产品版本；避免记录不必要的身份证、健康或完整保单信息。
- 产品回答引用批准资料版本；动态保单字段通过 Java 实时查询。数据库、模型提供商和日志保留策略分别治理。

## 实施规则

以上为新 AI 功能的目标结构。先按职责新增包和接口，不为目录结构做无收益的大规模搬迁；当某层有独立职责、可单独测试或替换时再抽象。新增产品推荐和保单问答时，先定义 API 契约与数据权限，再写 Agent 工具。

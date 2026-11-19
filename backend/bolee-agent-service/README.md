# Python AI Agent 服务

本服务是 Insurance Bridge 后端中的 AI 对话组件，位于 Maven 工程之外。它基于 Python、FastAPI 和 Agno / AgentOS，负责对话编排及受控的业务工具调用。

## 组件职责

- Java 保险服务负责客户身份校验、保险业务数据和受权的内部 API。
- Python Agent 负责组织对话、调用获准的 Java 工具接口并生成答复。
- AgentOS 会话与运行记录使用 PostgreSQL 持久化；PostgreSQL 部署镜像提供 pgvector 扩展。
- 模型推理由外部模型服务提供，模型 API 凭据通过运行环境注入。

Agent 的业务信息以 Java 服务返回的数据为准。它不直接查询保险业务 MySQL，也不能自行给出产品承保结论、保险条款解释或理赔决定。

## 运行依赖

- Python 3.11 或更高版本。
- Agno / AgentOS、FastAPI、HTTPX、PyJWT、SQLAlchemy 和 psycopg；版本范围见 `pyproject.toml`。
- PostgreSQL；使用向量能力时需要 pgvector 扩展。
- Java 保险服务及 OAuth 2.0 服务端，用于受权的内部工具调用。
- 可用的 LLM 服务及其 API 凭据。

产品目录、需求驱动的产品推荐、本人保单查询和客服对话是当前 Agent 的主要能力。数据范围、身份校验和读写权限仍由 Java 服务及其授权策略控制。

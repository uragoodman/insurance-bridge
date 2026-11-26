# 当前 Agent 系统架构

> 本页描述 `full_with_agent` 分支的现行实现。功能矩阵与部署限制以[当前能力说明](agent-capabilities.md)为准。

## 服务边界

```text
浏览器 / 客户端
  → Java insurance-app：登录身份、会话、客户 API
  → Python FastAPI + Agno AgentOS：任务理解、上下文控制、工具编排、回答生成
       → Java internal APIs：产品目录/推荐/比较/本人保单/条款来源和任务
       → PostgreSQL：Agno 会话/运行/trace 与独立的 insurance_terms pgvector schema
  → MinIO：保险条款原始文件，仅 Java 存储适配器访问
  → Ollama BGE-M3（本地）：条款 embedding
  → 配置的 LLM provider：聊天和摘要生成
```

浏览器不直接调用 AgentOS、Java 内部工具 API、MinIO 或条款数据库。Java 是客户身份、保险业务事实和授权的权威；Python 不直连保险业务 MySQL。

## Python 实际目录

```text
app/
  main.py                 FastAPI 路由、运行依赖装配、HTTP/SSE 响应
  agent.py                单个 Agno Agent 定义
  agent_instructions.py   系统说明
  schemas/                HTTP、工具参数和服务返回的数据契约
  domain/                 任务状态、会话上下文等 Agent 领域模型
  contexts/               单次 run 的 Java 授权、任务状态和证据收集
  tools/                  模型可见工具入口及具体工具适配器
  services/               对话、上下文、策略、产品能力和条款用例
  clients/                Java 内部 API、OAuth token 和索引任务客户端
  security/               AgentOS 管理路由和 OAuth scope 检查
  infrastructure/         Agno/PostgreSQL/pgvector/embedding/解析适配
  workers/                后台条款索引 worker
db/                       AgentOS PostgreSQL 连接与数据库集成
tests/                    Python 单元及链路契约测试
evals/                    意图/工具路由评分标准、用例和运行结果
knowledge/synthetic/      本地演示语料，不是正式保单条款
```

`app/main.py` 负责 FastAPI 装配及 HTTP 边界；`app/services/` 和 `app/tools/` 承载可单独替换、测试的用例与 Agno 工具适配。现状不采用计划文档里曾提议的 `api/application/agents` 目录树，也不需要为此做全量迁移。

## 单 Agent 的受控任务工具

当前 Agent 只注册 `CustomerTaskTools`。LLM 通过有类型约束的任务 JSON 选择 intent/action 和参数，`CustomerTaskDispatcher` 交给对应工具适配器，`CustomerTaskExecutionPolicy` 在调用 Java 前执行确定性校验，例如：推荐前必须读取 Java 已发布条件；比较对象必须来自本轮产品目录；指定保单必须来自本人保单列表。当前支持产品目录、产品推荐、产品比较、产品条款问答、本人保单五类意图。这是单 Agent 的工具路由，不是 multi-agent 运行图。

## 业务数据访问与授权

- Java 客户 API 从已认证会话确定 `user_id` 和 AgentOS session，不信任客户端或 LLM 提交的身份。
- Java 到 Python 的聊天使用独立 OAuth scope；Python 到 Java 的各业务工具按用途使用服务 token 和窄 scope。本人保单和条款读取还要求 Java 的会话级授权检查。
- 模型拿不到 OAuth secret、Java 授权 grant 或任意 URL/SQL 执行能力；AgentOS 管理接口走单独的管理 token 和 Python allowlist。
- 客户产品/保单事实不放在长期 LLM 记忆中；需要最新事实时调用 Java。

## 条款 RAG 数据流

```text
管理端上传 → Java MinIO 私有对象 + MySQL 来源/版本元数据
  → 独立审核 → Java 发布并入队
  → Python worker 领取 Java lease、校验 SHA-256、取原件
  → 解析 / 结构切分 / BGE-M3 embedding
  → PostgreSQL insurance_terms pgvector
  → Java 按产品与档案版本给出来源白名单
  → Python 硬过滤检索、证据约束回答、返回来源引用
```

业务侧的条款来源和发布状态由 Java 管理；pgvector 是版本化检索副本。检索必须固定 Java 返回的 `product_id`、`profile_version_id`、来源类型和状态。当前合成资料以 `SYNTHETIC` / `DEMO_ONLY` 标识，和正式合同语料隔离。原始文件留在 MinIO；数据库里保存 chunk、哈希和引用元数据。

## 当前限制

- 本地演示数据、已审核发布数据和正式保险公司合同是不同状态；不可混称。
- 部分产品的业务目录字段质量需要产品人员核对；目录快照不足时，Agent 应拒绝推断责任细节。
- 报价、理赔、承保、投保和保全操作尚未接入。
- PostgreSQL 备份的留存、恢复时的删除墓碑重放和模型提供商的数据治理需要在部署侧落实。

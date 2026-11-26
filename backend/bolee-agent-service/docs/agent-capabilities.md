# 保险客服 Agent 当前能力

> **现状基线：2026-11-25。** 本文只描述当前代码链路和本地配置状态；产品数据发布、服务可用性、条款来源索引状态可能变化，以运行时接口和数据库状态为准。`knowledge/synthetic/` 中的文档是本地演示语料，不是保险公司的正式合同。

## 当前实现

| 能力 | Python/Agno 实现 | Java 权威职责 | 状态与限制 |
| --- | --- | --- | --- |
| 客户聊天 | 单个 Agno Agent；LLM 只看到一个 `customer_task` 工具。支持同步和 SSE 流式响应。 | 校验客户身份、会话归属和调用授权，将内部请求转发到 AgentOS。 | 已实现；不能把 AgentOS 管理 API 直接暴露给客户。 |
| 产品目录 | 通过任务动作查询分类、搜索产品，结果校验后结构化返回。 | 提供当前产品目录和候选结果。 | 已接入；具体可查内容取决于 Java 本地数据。 |
| 产品推荐 | 先读取 Java 发布的推荐条件，再提交结构化需求；本轮策略阻止跳过条件读取。 | 决定匹配规则、产品事实和候选结果。 | 代码链路已接入；候选质量取决于已审核发布的产品属性和规则。不是报价或承保决定。 |
| 产品比较 | 先用 Java 目录解析本轮比较对象，再请求比较数据。 | 校验产品身份并提供比较事实。 | 已接入；相似产品建议必须经用户确认后才能比较或查询条款。 |
| 产品条款问答 | Python worker 解析、切分、生成 embedding 并写入隔离的 pgvector schema；查询按 Java 返回的产品、档案版本和可检索来源做硬过滤；答案绑定证据和结构化引用。 | 管理条款来源、版本、授权与任务；发布时入队索引任务。 | 本地演示库共有 28 份已索引来源：旧数据 4 份，新补充 24 份；新增来源对应 436 个 pgvector chunks。新数据通过 `LOCAL_DEMO_SEED` 标记的演示种子流程激活，未执行人工审核。`SYNTHETIC/DEMO_ONLY` 不代表正式保险条款。 |
| 本人保单查询 | 支持查询本人保单列表、指定保单摘要和经 Java 校验的已选保单；模型不能自行指定其他客户身份。 | 从登录用户身份确定数据归属，每次读取检查权限并最小化返回字段。 | 工具链路已接入；以 Java 业务数据实际返回为准。 |
| 上下文控制 | 按预算构造会话上下文；超预算时调用 LLM 生成摘要并校验后再淘汰旧轮次；会话轮次加锁，持久化失败不覆盖已完成回复。 | 管理客户会话和生命周期接口。 | 已实现；估算预算不能替代 provider 返回的 token 用量监控。 |
| AgentOS 管理与留存 | 对管理路由按方法/路径执行 OAuth scope allowlist；提供受保护的历史、会话删除和 trace 清理接口。 | 将管理员当前角色映射为短期 AgentOS 管理 token，并提供会话清理任务。 | 已实现；生产环境还需单独治理 PostgreSQL 备份留存与删除墓碑恢复。 |

## Agent 执行链路

```text
FastAPI /internal/chat 或 /internal/chat/stream
  → ContextAwareChatApplicationService（预算、压缩、会话锁）
  → Agno Agent（只注册 CustomerTaskTools）
  → CustomerTaskDispatcher + CustomerTaskExecutionPolicy
  → 目录 / 推荐 / 比较 / 条款 / 本人保单工具适配器
  → Java 内部 API；条款证据由 pgvector 检索
```

当前是**单 Agent + 显式任务工具**，不是多个 Agent 协作。意图由模型通过 `CustomerTaskRequest` 表达，Python 策略校验动作是否与意图匹配、是否满足先查目录/条件等工作流约束；Java 仍是保险业务事实和用户授权的最终边界。

主要代码位置：`app/main.py`（HTTP 入口与依赖装配）、`app/agent.py`（Agno Agent）、`app/tools/`（模型工具与适配器）、`app/services/`（用例和策略）、`app/clients/`（Java/OAuth 客户端）、`app/domain/` 与 `app/contexts/`（任务/上下文模型）、`app/infrastructure/`（Agno、PostgreSQL、embedding 和文档解析适配）、`app/workers/`（条款索引 worker）。

## 明确未接入

- 在线报价、投保、核保、支付、退保、保单变更。
- 理赔申请、理赔进度等可改变或承诺业务结果的操作。
- 真实人工客服转接链路。
- 扫描版 PDF 的 OCR。

## 条款文件管理与索引

Java 管理端接口位于 `insurance-mgt`：`/insurance-terms/sources` 支持上传草稿、提交审核、独立审核、发布、撤回及索引同步重试；经网关的路径为 `/api/insurance-mgt/insurance-terms/sources`。正常发布会创建 Java 索引任务，由 Python `agno-term-indexer` 领取、解析、切分、embedding 并写入 PostgreSQL/pgvector。本地演示产品档案版本由 seed 创建；目前这 24 份条款来源和档案通过 `LOCAL_DEMO_SEED` 激活，明确没有人工审核，不能冒充正式业务发布。当前 admin 登录返回 HTTP 500，因此本轮没有通过管理 UI/API 完成逐份人工审核。运行状态以 Java 来源表、任务表和 PostgreSQL `insurance_terms` 为准。

本地索引状态应分别检查 Java `tab_insurance_term_source.index_status`、任务表和 PostgreSQL `insurance_terms` 记录，不能仅凭 Markdown 文件存在判断已发布或可检索。

## 生产边界

- 正式回答只允许使用 Java 认可且与产品/版本绑定的正式来源；合成语料只用于本地演示和隔离评估。
- 产品与保单的动态事实由 Java 查询；模型记忆和 pgvector 不能替代业务数据库。
- 必须保持 AgentOS loopback/private 网络边界；客户聊天 OAuth、业务工具 OAuth、索引 OAuth 和运维 OAuth 分离。
- 部署时核验认证、Redis/MinIO/PostgreSQL、embedding、LLM 超时与数据留存配置；不能仅凭代码存在宣称生产就绪。

## 相关文档

- [当前系统架构](architecture.md)
- [系统分层与依赖方向](system-layering.md)
- [条款 RAG 设计与本地操作](insurance-terms-rag-plan.md)、[运行配置](insurance-terms-rag-operations.md)
- [文档索引](README.md)

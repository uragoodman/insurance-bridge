# AI 系统分层与依赖方向

> 这是当前代码的分层说明及后续扩展约束，不是要求一次性改造成其他目录结构的重构计划。

## 系统边界

采用 Java 业务服务 + 独立 Python Agent 服务。前端只访问 Java；Java 是身份、权限、保险产品和保单事实的唯一权威；Python 负责对话、任务路由、上下文和 LLM/工具编排；Agno PostgreSQL 保存 Agent 运行数据及隔离的条款检索副本。Python 不直连业务 MySQL。

```text
前端 → Java 客户 API / 授权上下文 → Python FastAPI / Agno
                                           ├─ Java 类型化内部 API
                                           ├─ Agno PostgreSQL
                                           └─ 条款 pgvector 查询
条款管理端 → Java → MinIO；Java 发布事件 → Python 索引 worker → pgvector
```

## Python 当前分层

| 目录 | 当前责任 |
| --- | --- |
| `app/main.py` | FastAPI HTTP/SSE 边界、依赖装配和响应组装；长业务规则应继续移至 service。 |
| `app/agent.py`、`app/agent_instructions.py` | Agno Agent 与提示词/行为约束。 |
| `app/schemas/` | API、任务工具和 client DTO 的结构校验。 |
| `app/domain/` | 任务流程、上下文预算等领域状态和值对象。 |
| `app/contexts/` | 单次模型 run 的授权、任务状态、证据和结果收集依赖。 |
| `app/tools/` | Agno 工具适配和参数映射；不承担 Java 权限决策。 |
| `app/services/` | 对话用例、上下文压缩、工具路由策略、产品/保单/条款应用逻辑。 |
| `app/clients/` | Java 内部 HTTP 和 OAuth 调用封装；声明端口接口在 `services/ports.py`。 |
| `app/security/` | AgentOS 管理 API 的 JWT、scope 与 route/method allowlist。 |
| `app/infrastructure/` | Agno 运行器、上下文 store、PostgreSQL 锁、embedding、解析和 pgvector repository。 |
| `app/workers/` | 不在客户请求线程中的条款索引任务消费。 |

新功能优先延续这些实际边界。只有当 FastAPI 路由规模或职责确实需要独立组合时，才抽取 router；不要为了目录形式迁移已有可工作的模块。

## 工具调用的约束

Agno 当前只暴露一个类型化 `customer_task` 工具。模型产生结构化 intent/action；Python dispatcher 负责映射已有处理器，确定性策略阻断非法 intent/action 和跳步工作流。产品目录、推荐、比较、本人保单事实与条款来源授权通过 Java 类型化内部接口访问。Prompt 不是授权控制；Java 必须对调用者、scope、客户归属、来源版本和租约继续校验。

不得向模型开放任意 SQL、任意 URL、任意 Java 方法或身份参数。工具结果按任务需要裁剪并记录证据引用；模型不得从记忆中生成当前保单事实。

## 数据所有权

| 数据 | 权威存储 | Python 侧用途 |
| --- | --- | --- |
| 产品、投保、交易、客户和保单 | Java 业务 MySQL/业务 API | 通过 Java API 读取经授权的最小字段。 |
| 客户会话所有权、授权 grant | Java 数据库与 Redis | 由 Java 验证后传入每轮 run；Python 不把 grant 放进 prompt。 |
| Agno session/run/trace | AgentOS PostgreSQL | Agent 运行数据；按 Java 会话生命周期和配置留存策略清理。 |
| 已发布条款原件 | MinIO 私有 bucket，由 Java 元数据管理 | 索引 worker 通过已租约的 Java task API 下载原件。 |
| 条款 chunk / embedding | PostgreSQL `insurance_terms` schema | 仅作检索副本；必须按 Java 的产品、档案版本和来源白名单硬过滤。 |

## API 与安全边界

- Java 客户 API 先认证身份，再签发短期、会话绑定的 Agent 授权；Python 服务只接受允许的 service client/scope。
- Python 到 Java 的 client 按权限分离：聊天工具、索引任务、operator/admin 分开；不复用宽权限身份。
- 本人保单访问必须由 Java按当前认证身份做 owner check；不能采信模型生成的用户 ID 或保单 ID。
- AgentOS 管理路径与客户 `/internal/chat` 隔离；管理端 token 仅由 Java 按当前登录角色签发，Python 再按路由和方法做 allowlist。
- 合成条款必须显式标为 `SYNTHETIC` / `DEMO_ONLY`；本地演示可按配置检索，但绝不能当成保险公司的正式合同事实。
- 记录 request/session/tool/status/timing 等必要运行信息，避免把完整身份证、病历、客户资料或 OAuth 凭据写入模型上下文或普通日志。

## 扩展规则

增加新任务时，先在 `schemas/customer_task.py` 定义受限动作和输入，再在 `CustomerTaskExecutionPolicy` 明确允许的 intent/action 与前置证据，随后增加独立工具适配器和 service/client 接口，并由 Java 提供相应权限检查和数据 API。先加有针对性的测试与评测用例，再扩大模型可调用面。LLM 输出只能提出候选动作，最终执行必须通过确定性校验。

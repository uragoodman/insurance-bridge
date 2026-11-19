# Python Agent 会话上下文管理

## 行为

- AgentOS 继续保存完整会话记录，但保险客服 Agent 不再自动将原始历史注入模型上下文。
- 每轮上下文由应用服务从 Agno session state 读取一份有版本、有限长的记忆快照，并通过 Agno 的 `session_state` 注入。
- 快照包括用户明确表达的会话目标、旧对话摘要、少量近期安全对话、当前话题、用户明确确认的原话、最近一轮待澄清问题，以及来自 Java 校验结果的产品 ID/版本引用。新用户轮次会清理上一轮待澄清问题；显式切换话题会清除旧目标、确认项和业务引用。业务引用最多保留 3 个用户轮次，仅用于后续重新查询，不代表当前业务事实。
- 预算由 `ContextBudgetPolicy` 做供应商无关的估算；接近软阈值时，`ContextManagementService` 将较早的安全对话交给无工具摘要 Agent 压缩。只有摘要成功且通过来源校验后才移除原轮次；摘要失败或为空会保留旧状态并返回可重试错误，不会继续运行并静默丢弃对话。
- 工具结果在进入模型上下文前按估算 token 上限检查；超限时不截断业务事实，而是返回 `RESULT_TOO_LARGE`，要求 Agent 缩小查询范围。预算中为工具结果累计预留 token。
- 本人保单调用、常见个人敏感字段或相关敏感话题会跳过本轮记忆写入。工具响应原文从不作为摘要输入；动态业务事实仍由 Java 内部接口实时提供。
- 同一用户会话从读取状态到 Agent 执行和状态保存由 PostgreSQL advisory lock 串行化，适用于多 worker 服务。
- 会话状态保存在 Agno session state 中，因此现有会话删除和过期清理一并清除上下文记忆，无需新增业务表。

## 配置

| 环境变量 | 默认值 | 用途 |
| --- | ---: | --- |
| `AGNO_CONTEXT_WINDOW_TOKENS` | `32768` | 配置的模型上下文总预算 |
| `AGNO_CONTEXT_FIXED_TOKENS` | `6000` | 系统提示、工具定义等固定部分的保守预估 |
| `AGNO_CONTEXT_OUTPUT_RESERVE_TOKENS` | `4096` | 为模型输出保留的 token |
| `AGNO_CONTEXT_SAFETY_MARGIN_TOKENS` | `2048` | 估算误差余量 |
| `AGNO_CONTEXT_TOOL_RESULT_RESERVE_TOKENS` | `8192` | 为同一轮工具结果预留的输入 token |
| `AGNO_CONTEXT_SOFT_LIMIT_RATIO` | `0.75` | 开始压缩的输入预算比例 |
| `AGNO_CONTEXT_RECENT_TURNS` | `6` | 最近安全对话触发整理的轮数 |
| `AGNO_CONTEXT_KEEP_RECENT_TURNS` | `2` | 压缩时优先保留的近期轮数 |
| `AGNO_CONTEXT_SUMMARY_MAX_CHARS` | `4000` | 持久化摘要长度上限 |
| `AGNO_CONTEXT_SUMMARY_MODEL_ID` | 主模型 | 可选的摘要模型 ID |
| `AGNO_TOOL_RESULT_MAX_TOKENS` | `1200` | 单次 Java 工具结果进入模型上下文的估算 token 上限 |
| `AGNO_CONTEXT_SESSION_LOCK_TIMEOUT_SECONDS` | `30` | 同会话等待锁的最长时间，超时返回冲突 |

Token 计数是面向 OpenAI-compatible 模型的保守估算，不是供应商精确计数，也不构成硬上限。它包含固定提示预留、显式记忆和当前用户消息；工具返回按单次上限检查并从输入预算扣除累计预留。Agno 及模型供应商在实际请求中仍可能增加额外 token。部署时应根据实际模型 usage 校准 `AGNO_CONTEXT_FIXED_TOKENS`、工具结果预留、总窗口及输出预留。

## 分层

- `app/domain/conversation_context.py`：快照、对话轮次和纯预算策略。
- `app/services/context_management.py`：安全筛选、触发压缩、超预算降级和状态更新。
- `app/services/chat_application.py`：同步与流式共用的会话编排。
- `app/infrastructure/agno_context.py`：Agno session state 和无工具摘要模型适配。
- `app/infrastructure/agno_agent_runner.py`：关闭原始历史、注入显式记忆快照。
- `app/infrastructure/postgres_session_lock.py`：跨进程会话轮次锁。

摘要调用失败或空结果时保留原始轮次并返回可重试错误；只有获得非空且来源校验通过的摘要后才淘汰其覆盖的轮次。若预算仍不足，会尝试将剩余记忆折叠为更短的有效摘要；不能安全压缩时拒绝本轮，不清空旧记忆。预算错误描述的是估算超限。记忆写入失败会记录日志，不把已经成功生成的答复变成可重试失败。

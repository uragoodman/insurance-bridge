# Python 保险条款 RAG 实施设计

> 实施状态（2026-11-22）：核心 Python 索引与检索链路、Agno 工具和 Java 引用 DTO 已实现。仍需配置数据库迁移/角色、OAuth、embedding provider 并完成集成验证；该状态不代表当前环境已可问答。

**状态：实现前设计。** Java 条款来源、审核发布和索引任务接口已存在；Python 消费者、条款专用向量表和 Agent 检索工具尚未实现。本文将现有 Python 分层和 Java v1 API 固定为实施边界，先做离线/集成测试，再接入客户 Agent。

## 目标与非目标

- Python 独立消费 Java 的 INDEX/REMOVE 任务，将经校验的原件解析、分段、生成 embedding 并写入 PostgreSQL/pgvector。
- 在线问答先由 Java 确认当前可检索的产品版本和来源，再在这些来源 ID 与 SHA-256 的白名单内执行混合检索。
- 结果包含真实来源、条款号、章节和页码等引用信息；没有证据、产品或版本不明确时不生成肯定的条款解释。
- 不改 Agno 会话/trace 表，不由 Python 访问 Java 业务库，不将合成资料混进正式产品问答，不在此阶段回答个案承保或理赔结论。

## 推荐 Python 目录

沿用当前包结构增量增加目录，不搬迁现有 Agent、聊天和工具代码：

```text
app/
  clients/
    java_term_index_api.py       # 后台 indexer 专用 OAuth client 和任务 API
  schemas/
    insurance_terms.py           # Java wire DTO、chunk、证据和检索响应
  services/
    terms/
      index_task_service.py      # 单任务用例：claim/download/process/complete/fail
      document_ingestion.py      # 校验、解析、结构化、切分和 embedding 编排
      retrieval.py               # Java 来源授权 + pgvector/全文混合检索
      ports.py                   # parser、embedding、term repository、Java API ports
  infrastructure/
    terms/
      postgres_term_repository.py
      parser_registry.py
      embedding_provider.py
  tools/
    insurance_terms.py           # Agno 只读 product_terms_search tool
  workers/
    term_index_worker.py         # 独立进程入口，不与 FastAPI 请求线程共用
db/
  migrations/                    # Alembic 版本迁移；不在应用启动时自动建表
```

`app/main.py` 继续只装配在线 Agent API，不在 FastAPI startup 中启动后台循环。Docker Compose 增加单独的 `agno-term-indexer` worker 服务，复用 Python 镜像与 PostgreSQL 网络，但仅注入其运行需要的 Java index OAuth 凭据和条款库连接配置。Worker 不需要 LLM chat key，也不需要 `product.terms.read` 或客户授权上下文。

## 外部契约

### Java 任务 API（后台 worker）

使用新建的 `JavaTermIndexApiClient`，不复用 `JavaInternalApiClient`：现有 client 面向在线工具，要求逐轮 `authorization_context`，且其路径白名单只覆盖 agent-tools。

| 操作 | Java API | Worker 行为 |
| --- | --- | --- |
| 领取 | `POST /internal/agent-index/v1/tasks/claim` | `200` 为裸任务 JSON；`204` 空队列且无 body，按退避/抖动等待后再轮询。Long ID 在线路上可能是十进制字符串，不经浮点转换。 |
| 下载 | `GET {contentPath}`，header `X-Task-Lease-Token` | 只接受 Java 返回的本站相对路径；流式写临时文件并核验字节数及 SHA-256。 |
| 续租 | `POST /internal/agent-index/v1/tasks/{taskId}/heartbeat` | `200` 返回 `ResponseResult<Boolean>`；检查 envelope `code == 200` 且 `data is true`。180 秒租约，建议每 45 秒续租；租约失败即取消后续写入，不能回调完成。 |
| 成功 | `POST /internal/agent-index/v1/tasks/{taskId}/complete` | `200` 返回 `ResponseResult<Boolean>`；同时检查 HTTP 状态、业务 `code` 和 `data is true`。JSON 提交原样 `leaseToken` 与 `sourceSha256`；只在 PostgreSQL 事务提交后调用。 |
| 失败 | `POST /internal/agent-index/v1/tasks/{taskId}/fail` | `200` 返回 `ResponseResult<Boolean>`，同样校验 envelope；只提交错误类别和清理过、限长的错误摘要，不包含原文或密钥。当前 Java fail 接口仅接收 reason 并重试至最多 8 次，确定性文件错误也会重复尝试。建议实现前给 failure request 增加 `retryable`（默认 true 兼容旧调用），不可重试错误首次即终止为 FAILED；保留错误类别并允许管理员显式重试。 |

Worker 通过 `AGNO_TERMS_INDEXER_CLIENT_ID` 和独立的 `AGNO_TERMS_INDEXER_CLIENT_SECRET` 以 client credentials 获取仅含 `product.terms.index` 的服务令牌。token 在内存中短期缓存；401 时最多刷新一次。网络失败按有上限的指数退避重试。领取请求超时可能已经成功领取，worker 不猜测任务内容、不伪造完成回调，等待租约到期后由 Java 重新分配。

### Java 在线条款 API

复用每轮工具上下文中的 `authorization_context`，并通过现有 Java client 的受限 GET allowlist 访问：

1. `GET /internal/agent-tools/v1/insurance-terms/products/{productId}/versions`
2. `GET /internal/agent-tools/v1/insurance-terms/products/{productId}/sources?profileVersionId={profileVersionId}`

以上两个 GET 均返回 Java `ResponseResult` envelope；Python 校验 HTTP 状态、`code == 200`、`data` 类型及 Pydantic DTO。版本 DTO 的 `profileVersionId` 是 JSON 字符串；source DTO 的实际 wire 字段为 `id`、`insuranceId`、`profileVersionId`、`sha256`、`sourceType`、`status`、`indexStatus` 和有效期等。Python schema 可用显式 Pydantic alias 映射 `id → source_id`、`insuranceId → product_id`，但不可把任务 DTO 的 `sourceId/productId` 与在线来源 DTO 混用。先由现有目录/推荐工具获得 Java 验证过的 `productId`。只有 Java 返回**唯一一个**当前有效条款版本时才自动继续；零个版本告知当前无可用条款；多个版本先澄清。调用 sources 时使用 Java 返回的 `profileVersionId`，不把目录 DTO 中展示用的 `productVersion` 误当内部版本 ID。Sources 列表是检索硬白名单；Python 必须校验来源 ID 与产品/档案版本归属一致，并限定 `OFFICIAL_PRODUCT_TERM` 与 Java 返回的可用状态。

第一版只支持当前有效的公开产品条款。历史保单版本只有在 Java 后续返回经 owner 校验的保单版本引用后再纳入；不能让模型或用户输入直接指定任意 `profileVersionId`。

## 索引处理流程

```text
独立 worker
  -> OAuth product.terms.index
  -> claim INDEX/REMOVE
  -> INDEX: 流式下载、校验 hash/size -> parser -> chunk -> embedding -> staging
  -> REMOVE: 先写 source tombstone，再删除该 source 的所有索引版本
  -> PostgreSQL 原子发布索引事务
  -> Java complete 回调
```

### INDEX

1. 校验任务字段、正数 ID、SHA-256 格式、允许的 MIME/文件后缀、`contentPath` 是固定 Java API 相对路径。下载最大字节数不超过 Java 给定 `fileSize` 及 50 MiB 配置上限。
2. 下载时流式计数和计算 SHA-256，核对任务 `fileSize`、`sourceSha256` 与 HTTP `Content-Length`（若存在）；不将原文写入日志。校验失败不进入 parser。
3. 通过 `ParserRegistry` 按扩展名选择 parser。PDF 需保留页码；DOCX 保留标题、段落和表格结构；旧 DOC 通过隔离的转换适配器处理；扫描 PDF 在 OCR 未启用或结果为空时明确失败，禁止静默写入空文档。
4. parser 输出统一 `ParsedBlock(text, page, heading_path, clause_id, clause_title, block_type)`。解析器设置文件字节、页数、提取文本量、运行时和临时目录上限；转换/OCR 进程设置硬超时。
5. 在同一条款号/子条款边界内切分。先保留短条款完整；长条款再按句子/段落切成可检索片段。一个 chunk 不跨免责/责任条款或章节。为召回附加父条款标题/必要定义上下文，但引用文本只截取实际原文；chunk 保留 `page_start/page_end`、`clause_id`、`heading_path` 和页内定位。
6. 批量请求单独配置的 embedding provider。聊天模型配置不等于 embedding API；冻结 `provider + model + dimensions + normalization/version` 后才建向量列。Embedding 失败不得标记索引成功。
7. 先创建 `STAGING` batch 并将 chunks 分批写入该 batch。完成后在短数据库事务中获取该 source 的 pg advisory lock，再确保/锁定状态行，确认 source 未 tombstone 且 hash 一致；事务中将新 batch 标成 `ACTIVE`、更新唯一活动 batch 指针、将旧 batch 标成 `RETIRED`。在线查询只 join 活动指针，因此切换是原子的；旧 batch 后台清理，不在发布事务里做大批量删除。若 source 已撤回，放弃新 batch、不得激活。最终激活前确认续租未丢失；提交后才回调 Java complete。

### REMOVE、重复投递和并发

- 按 `source_id` 幂等删除该来源的全部 hash、parser、chunker、embedding 版本，不只删除任务带来的某个 hash。
- 所有 INDEX/REMOVE 首先以一致算法取得 `pg_advisory_xact_lock(hashtextextended(source_id::text, 固定_seed))`，然后 `INSERT ... ON CONFLICT DO NOTHING` 创建状态行并 `SELECT ... FOR UPDATE`。首次并发创建状态行也由 advisory lock 串行化，不依赖“行尚不存在时的行锁”。
- REMOVE 在受锁事务中先把 `source_index_state` 置为 `REMOVED` 且清空活动 batch 指针，再分批物理删除该 source 的全部 batch/chunk；source tombstone 在物理清理期间持续阻止检索及旧 INDEX 激活。确认物理数据清理完成后才回调 Java complete。撤回后的 source ID 不允许重新激活；新条款版本使用新 `source_id`。
- 同一任务重复执行以数据库唯一约束、batch 指纹和活动指针切换保证幂等。若 PostgreSQL 已提交但 Java complete 网络失败，重试会重复写同一索引状态，然后再次回调。
- Java 已增加“撤回后旧 INDEX 回报完成再排 REMOVE”的补偿；Python 仍要以 source 锁/tombstone 防止并发乱序，并保证撤回期间的向量不会被在线查询泄漏（在线查询始终先取 Java 白名单）。
- 临时文件按任务完成/失败 finally 清理。`STAGING` batch 记录 `task_id`、心跳更新时间和创建时间；超过 24 小时无更新且非活动的 staging batch 才可清理；`FAILED`/`RETIRED` batch 按有限保留期清理，并在删除前确认没有 source 活动指针引用。
- 错误归类为可重试网络/Java/数据库/模型错误和不可重试格式/hash/OCR 空结果错误。前者使用有上限的本地/Java 退避；后者通过前述 `retryable=false` 终止，不重复 OCR。Java 终态失败在管理端可查看错误类别并由操作员显式触发 retry。
- 续租线程/协程设置不可逆的 `lease_lost` 信号；该信号触发后取消后续阶段，在 embedding 批次边界、staging 写入和激活事务入口都检查，不能再提交索引或回调 complete。激活事务应短且受单批 statement/transaction timeout 约束。

## Python PostgreSQL 数据模型

Agno 现有 `PostgresDb` 继续管理 sessions/runs/traces。条款数据放独立 `insurance_terms` schema，单独迁移版本；同一 PostgreSQL 容器可以承载，但运行角色拆分为 Agent API `SELECT` only、index worker 在条款 schema 内受限 DML、迁移账号 DDL。FastAPI 与 worker 不共用 schema owner/superuser。使用 Alembic 或等价的显式版本迁移；禁止在 Python 模块 import 或 FastAPI startup 时自动 `create_all`。

建议最小表：

### `source_index_state`

`source_id PK, source_sha256, state(INDEXED|REMOVED), active_batch_id NULLABLE, chunk_count, last_task_id, updated_at, last_error_code`。状态为 REMOVED 时活动指针必须为空，旧 INDEX 不可重激活。

### `term_index_batches`

`batch_id UUID PK, task_id UNIQUE, source_id, source_sha256, pipeline_fingerprint, state(STAGING|ACTIVE|RETIRED|FAILED), chunk_count, heartbeat_at, created_at, updated_at, error_code`。每个 source 同一时刻至多一个活动 batch；活动指针和 batch 状态在同一短事务切换。STAGING 断线回收按更新时间与活动指针判断；RETIRED、FAILED batch 有限期保留后清理。

### `term_chunks`

`chunk_id UUID PK, batch_id FK, source_id, product_id, profile_version_id, product_version, source_sha256, source_type, title, clause_id, clause_title, heading_path, page_start, page_end, chunk_index, text_content, search_vector TSVECTOR, embedding VECTOR(N), embedding_model, parser_version, chunker_version, tokenizer_version, pipeline_fingerprint, created_at`。

- 唯一键：`(batch_id, chunk_index)`；batch 唯一绑定 `task_id`，同一任务重试不得生成多个活动版本。
- 检索索引：`(source_id, source_sha256, profile_version_id)` B-tree；`search_vector` GIN；向量索引根据数据量和过滤召回评测决定。`search_vector` 的文档和查询两侧都先经过同一个中文 tokenizer，再以 `simple` 配置生成 tsvector/tsquery；不得假定 PostgreSQL 默认 FTS 能正确切分连续中文。tokenizer 名称/版本纳入 `pipeline_fingerprint`。若 tokenizer 评测尚未过关，v1 先做条款号/规范化关键词精确匹配 + 向量召回，不启用未经验证的中文 FTS。首批数据可先精确余弦距离排序，避免小语料中过早引入 HNSW 的过滤召回复杂度。
- 在线 SQL 必须 join `source_index_state.active_batch_id = term_index_batches.batch_id` 并要求 batch=`ACTIVE`，再与本轮 Java 返回的 `(source_id, source_sha256)` allowlist 精确相交；不能只按 source ID/hash 各自独立 IN。
- 不把 Java 的条款/产品事实复制成新的权威数据；chunk 元数据只用于筛选和引用，在线允许列表以请求时 Java 响应为准。
- chunk 存储原文引用所需的最少文本和定位；不存完整原文件副本，原件继续在 MinIO/Java。

Embedding 维度是表结构契约。首次实施前需确定在本地和部署环境都可访问、支持中文且具备向量输出的模型，并验证维度、超时、费用/资源、数据处理条款。同维度更换模型也必须由 active batch 限定单一 fingerprint，不能跨模型计算距离。改变维度需新列/新表或新 schema 完成平行迁移和重建，再切换查询；不得仅提升 fingerprint 后把不同维度塞入原 `VECTOR(N)` 列。

## 在线检索与 Agent 回答

新增 `InsuranceTermsService` 使用例级流程：

1. 确认用户指向唯一产品。产品名同名/不明确时复用目录工具澄清，不直接从全库做语义搜产品。
2. 通过 Java `versions`、`sources` API 建立本轮 allowlist。没有来源时返回 `NO_PUBLISHED_TERMS`；多版本则请求选择；Java 服务异常时 fail closed，不用 PostgreSQL 缓存绕过。
3. 在单条 SQL 里 join `source_index_state` 的活动 batch，限定唯一活动 `pipeline_fingerprint`，再把 Java allowlist 中对应的 `(source_id, source_sha256)` 对、精确 `product_id/profile_version_id` 和 `source_type=OFFICIAL_PRODUCT_TERM` 作为必需谓词，之后才排序向量与已验证的中文词项候选。条款号、责任/免责关键词、数值单位支持精确匹配优先；融合首版用 RRF/可解释加权排序，不引入额外 reranker 服务。
4. 只取 4–6 个片段，并按 `AGNO_TOOL_RESULT_MAX_TOKENS` 截断证据预算。结果为结构化 `Evidence`：本轮随机 evidence key、source ID/hash、产品/档案版本、条款号、页码、标题、逐字 excerpt、chunk ID。逐条核验 Java allowlist；任何未匹配 ID/hash/version 都丢弃并记安全计数。
5. 不将自由文本回答加一组 citations 视为已 grounding。LLM 按 schema 返回 `claims: [{text, evidence_keys}]` 和回答状态。Python 验证每条事实 claim 至少有一个 evidence key，且 key 属于本轮可信检索结果；无支持的 claim 拒绝或移除。客户端 citation ID、来源字段和引用文本由 Python 根据通过校验的 evidence key 从可信证据 map 生成，绝不接受模型自报 source ID/hash/page。无法产出有效 claims 时返回 `INSUFFICIENT_EVIDENCE`。条款正文是不可信文档输入，不能执行其中嵌入的命令；不补常识，不判断实际理赔。
6. 将引用作为结构化 `term_citations` 随 Python ChatResponse 传给 Java，由 Java DTO/Controller 白名单传给前端；引用不能从 LLM 文本反解析。需同步扩展 Python `ChatResponse`、Java `AgnoChatResponse`/`AiAssistantChatResponseVO` 和前端消费契约，定义字段名、引用数量上限、excerpt 字符上限和兼容旧响应策略。扩展 `AgentToolResultCollector` 时增加明确的 terms 结果类型和单独预算；目录工具可在同轮仅用于解析产品 ID，最终对外响应选择一个业务结果族，不能把中间结果误报为推荐结果，也不能因后续工具调用清空最终引用。

最小工具结果状态：`ANSWERED`、`NEEDS_PRODUCT_CLARIFICATION`、`NEEDS_VERSION_CLARIFICATION`、`NO_PUBLISHED_TERMS`、`INSUFFICIENT_EVIDENCE`、`JAVA_UNAVAILABLE`、`INDEX_NOT_READY`。拒答和澄清必须是明确状态，不能借模型记忆补答案。

## 可靠性、安全与可观测性

- API worker 与 index worker 分开部署、分别健康检查与扩容；worker 只在 Java 任务队列有负载时消耗 parser/embedding 资源。
- 建立超时：token 获取、Java HTTP、文件下载、每页 parser/OCR、embedding 单批、PostgreSQL statement；对可重试故障退避并受 Java lease 限制。
- 指标记录 claim 数、任务 action/status、下载校验失败、解析页数/耗时、chunk 数、embedding 耗时、续租失败、索引耗时、删除耗时、每个允许来源的过滤后 Recall 测试结果、无证据拒答率。日志只记录 task/source ID、错误码与耗时，不记录条款正文、access token、client secret 或用户授权上下文。
- 数据库分 schema 与角色；Java `product.terms.index` 服务身份只允许任务领取/回调/下载。在线 tool 持 `product.terms.read` + 每轮授权上下文，只访问正式条款 API。
- 降级行为以拒答和可重试为主：Java 授权失败不查询；embedding 服务不可用不假报 INDEXED；来源撤回时 Java 白名单立即为空，即便异步 DELETE 尚未完成也不能召回。

## 实施顺序与验收

### 阶段 A：契约和隔离存储

建立 typed task/source/evidence schemas、Java 两种 OAuth client、条款 schema migration、API 只读/worker DML/migration DDL 三类数据库角色及本地手工 smoke。冻结 embedding provider/model/revision/dimensions、parser/chunker/tokenizer 版本、pipeline fingerprint 与 query/document 双侧规范化策略；在连续中文条款上验证 tokenizer 后才打开 FTS，未验证时关闭 FTS。验收：路径白名单、scope 不可互换、裸 claim DTO 与 `ResponseResult` envelope 区分、Long-as-string 兼容、在线来源 alias 映射正确、embedding 维度和模型不混用、migration 可在空 schema 安装和重建。

### 阶段 B：索引 worker

完成 parser/embedding ports、任务 worker、租约 heartbeat、索引 staging/原子替换、REMOVE tombstone 和有限重试。先用 fake Java API、fake embeddings 和本地 PostgreSQL 做确定性测试，再使用真实 Java/MinIO/pgvector 做集成。

验收包括：哈希/大小错误拒绝；每个允许文件类型正确解析或返回明确格式错误；超时、租约丢失和 embedding 错误不完成任务；确定性错误可终止且可由管理员重试；DB commit 后 callback 失败可重试；同源 INDEX/REMOVE 竞态最终无活动或残留 chunk；重复任务不会重复活动 batch；孤立 staging 安全回收；重试和进程崩溃可收敛。

### 阶段 C：在线检索服务

实现 Java allowlist、产品/版本澄清、严格 SQL 过滤、混合检索、证据校验和工具预算。用包含相似产品名、不同版本对立条款、撤回来源及同条款号干扰项的人工标注集验收：产品/版本串检和 allowlist 外候选均为零；撤回后即使 pgvector 暂有旧行也不可见；引用字段逐项匹配原件；按产品/版本统计 Recall@K、MRR/nDCG、条款号 exact-match recall、金额/比例/等待期等关键字段召回率、claim 引用支持率和 unsupported-claim 拒答率。

### 阶段 D：Agent 与 Java 响应契约

将工具注册到 `app/tools/registry.py`，只对正式条款开放；更新 Agent system instructions、`AgentToolResultCollector`、Python ChatResponse 和 Java DTO 映射，结构化返回引用/澄清/拒答状态。用 fake LLM 测结构化边界，再进行一次受控真实 LLM smoke，确认回答只解释证据且不虚构引用。

### 阶段 E：本地端到端

手工执行 Python Alembic migration、启 PostgreSQL/Java/MinIO/两个 Python 容器；导入审核通过的原创测试条款，观察 Java INDEX → pgvector → 查询/引用，随后在管理端撤回并验证 Java 立即屏蔽、后台 REMOVE 最终删干净。正式 RAG 验收需由 Java、Python、前端显示引用三侧一起通过。

## 首次实现不做

- 不新增消息中间件、单独向量数据库或独立检索微服务；当前 Java outbox/claim/lease 契约和 AgentOS PostgreSQL 足够支撑首版。
- 不让 LLM 决定 SQL、产品 ID、来源 ID、版本 ID、有效状态或对象存储 URL。
- 不在启动时自动消费队列，不在用户聊天请求里临时下载/解析整份条款。
- 不让 Python 生成正式保险事实、自动审核或自动发布合成条款。

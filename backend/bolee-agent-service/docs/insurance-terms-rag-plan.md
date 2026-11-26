# 保险条款知识库引入方案

**状态：设计与治理基线。**截至 2026-11-25，本地演示环境已完成 Java 任务 → Python 索引 → PostgreSQL/pgvector 的联调，28 份 `SYNTHETIC` 演示来源已索引；最近新增的 24 份以明确标记 `LOCAL_DEMO_SEED` 的本地种子路径激活，未走人工审核。此状态不代表保险公司正式条款，也不等于线上生产发布。准确的代码边界与本地数据状态见[Agent 当前能力](agent-capabilities.md)。

首份模拟条款结构稿为[无忧综合意外险模拟条款 v1](../knowledge/synthetic/insurance-1721437008821260290-v1.md)。它采用常见条款章节结构，数据库可确认字段和模拟设定逐项区分；当前已作为演示来源索引，不是正式保险合同。

## 目标与边界

- 为产品责任、责任免除、等待期、保险期间、理赔材料等问题提供可追溯的资料检索。
- Java 继续拥有产品、用户、保单和授权事实；Python Agent 负责检索编排与自然语言解释。
- PostgreSQL/pgvector 中的 chunk 和 embedding 是可重建的检索副本，不是保险条款权威源。
- 公开条款样例只用于学习章节结构、字段和测试场景。不得将其他保险公司的产品条款复制后冒充本项目产品资料。
- 简历演示可用原创合成条款，但必须标记为 `SYNTHETIC`，只在演示/测试语料中检索，不得与正式来源混用或用于真实客户业务。
- 不把一般法律规定、行业示范条款或其他产品条款当作指定产品的合同约定；不回答核保、承保或个案理赔结论。

## 公开样例及用途

| 来源 | 可借鉴内容 | 不可据此推断 |
| --- | --- | --- |
| [国家金融监督管理总局《保险销售行为管理办法》](https://www.nfra.gov.cn/cn/view/pages/rulesDetail.html?docId=1129945) | 条款/产品说明需要覆盖的字段清单，例如产品名称、保障范围、期间、保费与交费方式、限额、责任免除、索赔程序，以及人身险等待期等；也强调条款结构清晰、准确、严谨、易懂。 | 它是监管要求，不是任何具体产品的保险责任或免责内容。 |
| [中国保险行业协会《驾乘人员意外伤害保险示范条款（2020版）》](https://www.iachina.cn/module/download/downfile.jsp?classid=0&filename=228d31e683f24ff2afb371d3d81d2c86.pdf) | 观察总则、保险责任、责任免除等条款结构，以及条款如何引用保险金额、事故时限和附表。 | 示范条款不能作为本项目某个产品的在售条款，也不能从中挪用数值填补产品缺失事实。 |
| [中国保险行业协会《人身意外伤害保险条款》](https://www.iachina.cn/col/col4392/index.html) | 观察保险责任、责任免除、保险金额和保险费、保险期间、保险人义务、理赔申请和释义等章节如何组织。 | 该网页不是本项目产品资料，也不证明其他险种适用相同条款。 |
| [中国太平洋保险《互联网个人住院 B 款短期医疗保险产品说明书》](https://www.cpic.com.cn/upload/resources/file/2025/06/10/87004.pdf?oldName=%E5%A4%AA%E4%BF%9D%E4%BA%92%E8%81%94%E7%BD%91%E4%B8%AA%E4%BA%BA%E4%BD%8F%E9%99%A2B%E6%AC%BE%E7%9F%AD%E6%9C%9F%E5%8C%BB%E7%96%97%E4%BF%9D%E9%99%A9%E4%BA%A7%E5%93%81%E8%AF%B4%E6%98%8E%E4%B9%A6.pdf) | 观察医疗险说明书中等待期、责任免除和条款交叉引用等结构；可据此设计“等待期问答”“免责条目召回”等测试题。 | 产品说明书不是完整合同条款；其中具体天数、比例、医院范围和除外责任只适用于该来源产品。 |

所有公开样例要保存来源 URL、抓取日期、文件 hash 和资料类型。若后续用于可检索测试集，必须标记来源产品与 `reference_only`，不能混进项目合成产品或被推荐工具返回。

## 本项目的所有权与服务边界

1. **Java MySQL / 产品文件服务：**维护规范产品 ID、条款来源、版本、有效期、审核/发布/撤回状态及审批人。原始合同文件放在对象存储中；本地开发使用独立 MinIO bucket `insurance-source-files`，正式环境再配置私有云 OSS/Kodo bucket。MySQL 只保存对象存储 provider、bucket、object key、文件名、MIME 类型、大小和 SHA-256 等元数据，不存文件二进制。现有产品上架状态不代表条款已经审核。
2. **Python AgentOS PostgreSQL：**保存已允许索引的条款段落、检索元数据和 embedding。增加独立的条款表/模式，不与 Agno `sessions`、`runs`、trace 表混用。向量索引可以删除后重建。
3. **Java 内部 API：**Java 将当前有效、已发布条款版本和 Java 验证过的产品引用提供给 Python；个人保单场景必须先由 Java 校验登录客户及保单归属，再提供该保单对应的产品版本。使用单独 scope（建议 `product.terms.read`，涉及本人保单时仍由 Java 进行 owner 校验），不把数据库凭证、用户 ID 或任意 SQL 暴露给模型。
4. **Python：**按类型化 Schema/Client/Service/Tool 分层。模型只能提供问题和已解析的产品引用；工具按已校验的 `productId + productVersion` 检索，不能自行修改过滤条件或选择相邻产品。

数据流：

```text
权威来源文件 → Java 登记版本/审核发布 → Python 受限入库与分段 → pgvector 检索副本
用户问题 → Java/目录工具确认产品 ID 与版本 → Python 按 ID/版本硬过滤检索 → 引用原文回答
```

本地开发复用外部 Compose 管理的 MinIO 容器 `deploy-minio-1`，宿主机 S3 API 为 `http://127.0.0.1:19000`，控制台为 `http://127.0.0.1:19001`。已在该 MinIO 实例中创建独立私有 bucket `insurance-source-files`，匿名访问关闭。该服务只负责原件存储，尚不代表 Java 文件模块或 Python Agent 已完成 MinIO SDK 接入。后续应通过专用存储适配器接入，不复用当前会将 OSS bucket ACL 设为公开读的上传实现。

`deploy-minio-1` 由单独的本地 Compose 项目管理，不在本项目根 `docker-compose.yml` 中重复声明，以免与现有 `19000/19001` 端口冲突。MinIO 凭证通过其本地 Compose 配置管理，不复制到本项目。若容器停止，可在该 Compose 项目中启动 MinIO 服务。

## 文档和版本数据模型

每份来源至少记录：

```text
source_id, product_id, product_version, source_type,
source_title, source_uri_or_file_id, content_hash,
effective_from, effective_to, approval_status,
reviewed_by, reviewed_at, published_at, withdrawn_at
```

每个 chunk 至少继承：

```text
chunk_id, source_id, product_id, product_version,
clause_id, clause_title, section_type, chunk_index,
effective_from, effective_to, approval_status, source_type,
content, embedding_model, embedding_version
```

- `source_type` 至少区分 `OFFICIAL_PRODUCT_TERM`、`REGULATORY_REFERENCE`、`INDUSTRY_SAMPLE`、`SYNTHETIC`。
- 条款版本不可原地覆盖。修订时新建版本并留存旧版本；正式产品默认只查当前发布且有效版本。本人保单解释按 Java 已核验的保单关联版本检索，不把“当前版本”替代投保时版本。
- 状态采用 `DRAFT → REVIEWED → PUBLISHED → WITHDRAWN`。只有审核通过且索引完整的版本才允许检索。撤回要同步禁用所有关联 chunk；索引不同步时先关闭该版本查询，再重建索引。
- 每个 chunk 保存原始条款编号/页码/标题与来源 hash，使答案引用可以回到原件。

## 合成条款语料的生成规则

1. 以结构化 JSON/YAML 作为唯一事实源，逐产品版本定义保障责任、触发条件、限额、等待期、免责、期间、理赔材料和释义等字段；同一事实不得在多个手工文本中分别维护。
2. 用受控模板渲染原创模拟条款。LLM 可辅助生成同义问法、对抗测试问题或初稿，不允许直接生成并自动发布保险责任/免责事实。
3. 建立跨字段一致性校验，例如等待期适用范围与免责条款一致、保障期间边界明确、责任描述不与免责描述冲突；未通过校验的版本不得进入检索索引。
4. 使用虚构公司、产品名和编号；首页、元数据和用户可见回答均标注“模拟资料，仅用于演示，不构成保险合同或投保依据”。
5. 建议先做少量多样的产品/版本，覆盖意外、短期医疗、旅行等不同结构；再增加相似产品名、相似免责文本和版本差异作为检索压力样例。数量不作为质量指标。

## 检索与防串条款规则

1. **先解析产品，后检索。** 产品名称没有唯一映射、用户没有指定产品、产品有多个有效版本或指代不明时，先追问；禁止退化成跨产品全库语义搜索。
2. **硬过滤先于相关性排序。** 检索查询必须由服务端固定 `product_id`、`product_version`、`approval_status='PUBLISHED'`、有效期和允许的 `source_type`，之后才能做全文/向量排序。相似度阈值和 reranker 不能替代这些约束。
3. **混合检索只在同一产品版本内使用。** 可先用 Postgres 全文检索与向量检索召回，再做融合和 rerank；对条款编号、产品代码、数值和定义词保留精确关键词检索。候选不足时返回不足，不放宽产品/版本过滤。
4. **证据必须携带来源。** 检索响应返回 `productId`、`productVersion`、`sourceId`、`clauseId`、页码/段落和原文片段。生成后校验所有引用都属于本轮已验证产品/版本；没有直接证据时回答“当前资料无法确认”。
5. **禁止合并冲突版本。** 同一问题命中两个互斥的已发布事实、来源状态冲突或条款引用缺失时，标记 `CONFLICT`/`INSUFFICIENT_EVIDENCE` 并拒答或转正式渠道，不选择更相似的一段自行解释。
6. **个案边界。** RAG 只解释资料内容；不根据健康资料或用户描述判断承保、免责适用、理赔是否应赔。本人保单状态、日期等动态事实继续通过 Java 实时工具读取。

pgvector 官方说明指出，近似 HNSW/IVFFlat 查询的过滤条件可能在索引扫描后应用，选择性过滤会导致返回少于 `LIMIT` 的结果；可按每个产品版本的数据量选择精确检索、合适的过滤列索引或迭代扫描，并通过实际召回测试校准。无论采用何种索引策略，SQL 的产品和版本过滤都必须保留。[pgvector filtering](https://github.com/pgvector/pgvector#filtering)

## 条款切分

- 首选按文档目录/标题层级、条款号和子款切分；一个 chunk 不跨越不同条款号或不同产品版本。
- 短条款保持完整；很长条款按子款切分，并给每段保留父条款编号、标题和必要的定义上下文。表格作为有标题/列名的结构化文本保存，数字与单位不得拆开。
- 为便于召回，可为每个 chunk 前置产品名、条款版本、章/条标题；该前缀是索引上下文，不替代数据库元数据过滤。
- 是否重叠切分由实际检索测试决定；不得用固定字符窗口把免责和保险责任拼成同一证据块。原始文本、规范化文本及解析定位信息都要保留。

## 验收门槛

发布知识库前至少通过以下测试：

- 产品 A 的查询只返回 A 对应 `productId/version` 的证据；构造名称相近的 B 产品验证无串召回。
- 同产品新旧条款内容相反时，普通咨询只命中当前有效版本；指定历史保单时只命中 Java 校验过的历史版本。
- 未发布、已撤回、未生效、已失效、资料缺失和版本冲突均不产生肯定答案。
- 责任、免责、等待期、数值/单位和条款号问题都能返回正确来源；答案中的每个关键断言至少有一条同产品同版本证据。
- 无答案或低证据覆盖时明确拒答；不得退回通用模型常识或其他产品条款补全。
- 建立带标注的问答集，分别评估目标条款 Recall@K、产品/版本过滤准确率、引用正确率和无依据拒答率；对产品/版本串检设置零容忍断言。
- 当前仓库的 Python 单元测试、Java 接口/授权测试及端到端检索测试都覆盖发布、撤回和跨版本情形。

## 分阶段实施

1. **模拟语料试点：**定义数据 Schema、版本/来源元数据与生成器；造一组原创模拟条款和相似干扰集，仅在本地演示环境启用。
2. **索引与检索：**在 Python AgentOS PostgreSQL 中增加隔离的条款 chunk/embedding 表，实现导入、切分、向量/全文混合检索和版本硬过滤。
3. **Java 发布边界：**新增条款元数据登记、审核/发布/撤回 API 与专用 scope；同步已发布版本到索引，增加索引状态、hash 核验和失败关闭策略。
4. **Agent 接入：**注册窄权限 `product_terms_search` Tool；对话流程先调用 Java 解析产品和版本，再检索；回答展示来源，结构化返回引用信息。
5. **正式资料替换：**仅在获得实际产品的官方完整条款、产品负责人审核、有效版本与撤回规则后，将对应来源标记为正式来源。合成内容始终与正式内容隔离。

## 当前实现进度

- 管理接口提供上传、分页列表、详情、私有下载、草稿/驳回资料编辑、提交审核、审核通过/驳回、发布、撤回及草稿/驳回资料删除。所有管理操作要求已授权操作员（`onlyAuthenticate == false`）。
- 文件只接受 PDF、DOC、DOCX、Markdown 和 TXT，默认上限 50 MiB；上传以流式计算 SHA-256，校验 PDF/Office 文件签名，避免把整个文件先复制到 JVM 堆。对象键由服务端 ID 生成，原件不公开 URL。
- 来源状态为 `DRAFT → PENDING_REVIEW → APPROVED/REJECTED → PUBLISHING → PUBLISHED → WITHDRAWN`。只允许审核通过的 `OFFICIAL_PRODUCT_TERM` 发布到 Agent 知识边界；合成、行业样例和监管参考资料不会通过 Agent 正式条款 API。
- 发布和撤回分别在同一 MySQL 事务中创建索引任务；后台 Agent 使用独立 `product.terms.index` OAuth 客户端领取、下载、完成或失败任务。发布只有在索引回执中的 SHA-256 与原件一致后转为 `PUBLISHED`；撤回会立即从 Java 正式资料 API 屏蔽，并异步清理索引。
- 领取任务响应包含 `leaseToken`、`leaseUntil` 和 `leaseDurationSeconds`（当前 180 秒）；worker 应在租约过期前续租。索引端必须先提交向量变更再回调完成；旧 INDEX 在撤回后回报完成时，Java 会再排一个 REMOVE，收敛可能发生的乱序写入。
- Java 内部只读 API 通过现有 OAuth scope `product.terms.read` 和逐轮授权上下文保护；可先列出产品当前有效且有索引的档案版本，再按精确 `profileVersionId` 读取条款来源/原件。未指定版本而存在多个有效版本时拒绝返回混合结果；原件读取校验产品、档案版本与 SHA-256。
- MinIO 由 `insurance.terms.storage.enabled` 显式启用。目标 bucket 必须由本地 Compose/部署基础设施预先创建；Java 条款服务账户不具备列举或创建 bucket 的权限，只允许对该 bucket 的条款对象执行读、写、删。凭证只放本机 `.env` 或部署密钥配置，不提交仓库。
- 数据库迁移为 `V20261121_01`（来源表）、`V20261122_01`（工作流与索引任务）和 `V20261122_02`（任务租约 fencing token 与追加式审计表），必须按仓库流程手动执行，应用启动不会自动建表。
- Python 已包含 Java leased task 消费、PDF/DOCX/DOC/MD/TXT 解析、条款感知切分、embedding、pgvector staging/active batch、Java 白名单检索、证据约束回答、结构化 citations 和 Agent 工具。扫描 PDF 暂不做 OCR，会返回明确失败。当前本地演示环境的 migration、OAuth、MinIO、embedding 与索引 worker 已完成联调；24 份新增数据由本地演示种子路径激活，Java 管理端正常人工审核发布入口仍可用，但本地 admin 登录异常时本轮未走该入口。管理端 Vue 源码不在此仓库。模拟文件必须保持 `SYNTHETIC` 标记，不得冒充正式保险条款。

启用前需先按仓库流程手动执行上述 Flyway migration，再将 MinIO 连接参数注入 `insurance-mgt` 与 `insurance-app` 进程环境（变量名见根目录 `.env.example`），并设置 `INSURANCE_TERMS_STORAGE_ENABLED=true`。根目录 `.env` 不会被 Spring Boot 自动读取；IDE 启动时应在 Run Configuration 中配置环境变量，或从本地密钥管理方式注入。配置授权服务时，`bolee-agno-tools` 需获批 `product.terms.read`，另外创建仅有 `product.terms.index` 的 `bolee-agno-terms-indexer`。迁移未应用时保持功能关闭。

真实产品条款的正确性与合法使用必须以产品权威来源及负责人审核为准。本地演示链路已验证索引完成；如果要将真实条款用于客户服务，仍须导入正式来源、完成独立审核，并在目标环境验证授权、发布、撤回和引用链路。

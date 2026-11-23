# 条款 RAG 本地配置与迁移

本文记录 Python 条款 RAG 首版的本地配置与启动步骤。当前本地 PostgreSQL schema 和隔离角色已配置，OAuth 的条款读取/索引客户端也已登记；`.env` 中保存本机值且已被 Git 忽略。Ollama 与 BGE-M3 已启动，宿主机、Compose 网络及 Python embedding adapter 均已验证返回 1024 维向量。Java `insurance-app` 已加载索引 client ID，索引 worker 正在运行并成功轮询 Java 任务接口；当前尚无待处理任务。真实上传与发布需使用管理端授权操作人身份。

## 外部配置

在仓库根目录 `.env` 中设置：

- `AGNO_TERMS_DATABASE_URL`：Agent API 查询角色的 PostgreSQL URL，只授予 `insurance_terms` schema 的 `USAGE` 和表 `SELECT`。
- `AGNO_TERMS_INDEX_DATABASE_URL`：独立索引 worker 的 PostgreSQL URL，只授予条款表读写权限。
- `AGNO_TERMS_INDEXER_CLIENT_ID/SECRET`：Java 服务 OAuth client，scope 为 `product.terms.index`。
- `AGNO_TERMS_EMBEDDING_BASE_URL/API_KEY/MODEL/DIMENSIONS`：支持 OpenAI-compatible `/v1/embeddings` 的中文向量服务配置。模型和向量维度必须保持一致。本地默认使用 Ollama 的 BGE-M3，输出 1024 维向量。
- `AGNO_TERMS_INDEXER_JAVA_URL`：Java insurance-app 可从 Compose 网络访问的 origin。

数据库 URL 中的特殊字符应进行 percent-encoding。`.env.example` 使用相同本地演示账号作默认占位，实际部署应配置两个分离账号，且迁移账号不得复用应用账号。

## 本地 embedding 服务

根 `docker-compose.yml` 提供独立的 `terms-embedding` Ollama 服务，只将 11434 端口绑定到本机回环地址，模型数据保存在 `bolee-cloud_terms_ollama` volume。首次启动并下载 BGE-M3：

```powershell
docker compose up -d terms-embedding
docker exec bolee-terms-embedding ollama pull bge-m3
```

Python 容器通过 `http://terms-embedding:11434/v1` 访问兼容的 `/v1/embeddings` 接口；`ollama` 是本地占位 API key，Ollama 会忽略该值。BGE-M3 的 1024 维度与 `.env` 的 `AGNO_TERMS_EMBEDDING_DIMENSIONS` 一致。

## 安装 schema

先启动数据库：

```powershell
docker compose up -d agentos-db
```

用迁移角色将 `bolee-agent-service/db/migrations/001_insurance_terms.sql` 应用到 AgentOS PostgreSQL 数据库。该脚本创建 `vector` extension 和 `insurance_terms` schema，不会自动运行。迁移后为独立 reader/indexer 角色授权；索引表包含正式条款摘录，reader 不应有写权限，worker 不应有 DDL 权限。

角色权限示例（在管理员 psql 会话中执行，使用部署密钥替换密码）：

```sql
CREATE ROLE insurance_terms_reader LOGIN PASSWORD 'replace-with-secret';
CREATE ROLE insurance_terms_indexer LOGIN PASSWORD 'replace-with-secret';
GRANT USAGE ON SCHEMA insurance_terms TO insurance_terms_reader, insurance_terms_indexer;
GRANT SELECT ON ALL TABLES IN SCHEMA insurance_terms TO insurance_terms_reader;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA insurance_terms TO insurance_terms_indexer;
ALTER DEFAULT PRIVILEGES IN SCHEMA insurance_terms
  GRANT SELECT ON TABLES TO insurance_terms_reader;
ALTER DEFAULT PRIVILEGES IN SCHEMA insurance_terms
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO insurance_terms_indexer;
```

运行迁移的账号需有创建 extension/schema/table/index 权限。完成后将两个应用 URL 分别指向 reader 和 indexer 账号，再启动服务：

```powershell
docker compose up -d agno-agent agno-term-indexer
```

`agno-term-indexer` 使用 `antiword` 解析旧版 DOC；PDF、DOCX、MD、TXT 由 Python 库处理。扫描版 PDF 当前明确返回 OCR 不可用的永久失败，不会被当成空条款索引。

## 当前限制

- 尚未配置 OCR。扫描 PDF 必须先转为可检索 PDF 或 DOCX/TXT 后重新提交。
- 旧 DOC 转换保留文本与分页符，不能保证原 Word 页码/表格版面完全一致；回答引用仍提供来源标题、条款号及可用页码。
- 本地 AgentOS 默认 PostgreSQL 用户是开发启动便利配置，不是 reader/worker 隔离的部署示例。
- 管理端 Vue 源码不在本仓库；Java 会透传结构化 `termCitations`，但前端引用卡片展示仍需要在实际 UI 源码仓库完成。

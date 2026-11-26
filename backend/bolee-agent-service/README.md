# Bolee AgentOS Service

This Python service adapts Agno's official `agentos-railway` starter for the Bolee insurance platform. Agno AgentOS runs the agent API; PostgreSQL stores conversation sessions and traces. Java `insurance-app` remains the customer-facing API and calls the private `POST /internal/chat` route after authenticating the user.

The canonical development and demonstration addresses and request chain are documented in [AI chat environments](../docs/ai-chat-environments.md).

## Start with Docker Compose

From the repository root, copy `.env.example` to `.env`, set the two OAuth2 client secrets and `AGNO_MODEL_API_KEY`, then start the Python service and its PostgreSQL database:

```powershell
docker compose up -d agentos-db agno-agent
docker compose logs -f agno-agent
```

The API is bound to `127.0.0.1:8000`; PostgreSQL is bound to `127.0.0.1:15433` (mapped to container port `5432`). Compose stores database files in the named `agno_pgdata` volume. To stop the services while preserving data, run `docker compose stop agentos-db agno-agent`.

For a host-run Python process, point `AGNO_DB_HOST` to `127.0.0.1` and `AGNO_DB_PORT` to `15433`. Compose sets these to the internal database hostname and port (`agentos-db:5432`).

## Java connection

Use separate public-key URLs for each network location: Python in Compose uses `AGNO_OAUTH_TOKEN_KEY_URL` (normally `http://host.docker.internal:7078/oauth/token_key`); host-run Java uses `AGNO_JAVA_OAUTH_TOKEN_KEY_URL` (defaults to `http://127.0.0.1:7078/oauth/token_key`).

Register confidential OAuth clients in the authorization server's `tab_client_details`: `bolee-java-agent-chat` with only `agent.chat`, `bolee-agno-tools` with `product.catalog.read`, `policy.own.read`, and `product.terms.read`, a dedicated `bolee-agno-terms-indexer` with only `product.terms.index`, `bolee-agno-tag-generator` with only `product.tags.generate`, and a separate cleanup client with only `agent.operator`. Do not reuse the cleanup identity for tag writes or indexing. Store multiple scopes in `tab_client_details.scope` as a comma-separated list (for example, `product.catalog.read,policy.own.read,product.terms.read`); Python's `AGNO_OAUTH_SCOPE` request value remains space-separated. Configure the tag-generation client ID in Java and Python as `AGNO_TAG_GENERATION_CLIENT_ID`, and its secret only in Java as `AGNO_TAG_GENERATION_CLIENT_SECRET`; the scope is `product.tags.generate`. Java and Python use different public-key URLs because they run on different networks: host-run Java uses `AGNO_JAVA_OAUTH_TOKEN_KEY_URL` (`127.0.0.1`), while Python in Compose uses `AGNO_OAUTH_TOKEN_KEY_URL` (`host.docker.internal`). Machine tokens come from the service-token endpoint, separate from the legacy user-login path. Keep the AgentOS API bound to loopback. Set `AGNO_SERVICE_URL=http://127.0.0.1:8000` when Java runs on the host, or `AGNO_SERVICE_URL=http://agno-agent:8000` when Java joins the Compose network. Host-run Java should use `127.0.0.1:7078` for its OAuth token endpoint; Python in Compose should use `host.docker.internal:7078`. The `insurance-app-dev.yml` Nacos config takes precedence over local defaults, so keep its `agno.service-url` and `agno.oauth.token-url` aligned with the host-run Java addresses. `start-local.ps1` imports `AGNO_*` values from the repository `.env` into host-launched Java processes; when launching from IntelliJ, set the same variables in the Run Configuration. For Python-to-Java tools, set `AGNO_JAVA_SERVICE_URL` to the host Java service; Compose defaults to `http://host.docker.internal:17065`.

The customer endpoint is `POST /ai-assistant/chat`, for example:

```json
{"sessionId":"support-123","message":"怎么查看保险产品？"}
```

Run the catalog service unit checks from this directory with `python -m unittest discover -s tests -v`.

Java derives the AgentOS `user_id` from the authenticated account; clients cannot select another identity through the customer API. Both service directions use short-lived OAuth2 `client_credentials` bearer tokens with distinct client identities and scopes. Python protects `/internal/chat` with `agent.chat`; Java catalog tools require `product.catalog.read`, own-policy tools require `policy.own.read`, and approved terms reads require `product.terms.read` plus the separate five-minute Redis chat grant. The indexing task API uses the dedicated `product.terms.index` OAuth client and does not accept customer chat grants. AgentOS management routes require a five-minute token exchanged at Java `/agentos/admin-token`; the exchange accepts the admin console `user-token` session header (or a Bearer access token), validates the active Redis login session, and loads current roles from `security-web`. Map `admin` to `AGENT_ADMIN`, `operator` to `AGENT_OBSERVER`, and `agent_conversation_auditor` to `CONVERSATION_AUDITOR`. Assign the latter only to approved internal auditors. Python enforces an explicit AgentOS route/method allowlist and validates it against generated OpenAPI at startup. Browser origins must be configured through `AGNO_AGENTOS_ADMIN_CORS_ORIGINS`. The chat client cannot mint or use this management token. Never put OAuth client credentials in source control or model context.

Before setting `AGNO_ENABLED=true`, enable Java AI metadata persistence, run its Flyway migration, configure positive `AI_RETENTION_CONVERSATION_DAYS`, `AI_RETENTION_TRACE_DAYS`, and `AI_RETENTION_CLEANUP_TASK_DAYS` values approved by the data owner, and provision a separate cleanup OAuth client with only `agent.operator`. Configure that client's ID/secret in Java as `AGNO_OPERATOR_CLEANUP_OAUTH_CLIENT_ID` / `AGNO_OPERATOR_CLEANUP_OAUTH_CLIENT_SECRET` and its ID in Python as `AGNO_OPERATOR_OAUTH_CLIENT_ID`. Java refuses to start when customer chat is enabled without the lifecycle store. Java session deletion removes matching Agno sessions/runs and their traces; scheduled jobs also purge expired legacy sessions and old trace/span rows in bounded batches. Database backups still require an independently managed expiry policy and a restore drill that reapplies deletion tombstones.

## Current agent scope

The Python service currently runs one Agno Agent and exposes a single typed `customer_task` tool to the model. A deterministic dispatcher/policy routes five intents: product catalog, recommendation, comparison, product-terms QA, and own-policy lookup. Python calls Java for product and policy facts; term retrieval uses the isolated PostgreSQL/pgvector index with Java-validated product/version/source metadata. The local demo catalog has 28 synthetic term sources indexed: 4 earlier sources and 24 recently added sources (436 chunks across the new sources). The 24 new sources were activated through the explicitly marked `LOCAL_DEMO_SEED` path because local admin login currently returns HTTP 500; they were not human-reviewed. Synthetic sources are demo material, not official insurer wording. Claims, quote, underwriting, purchase, cancellation, and policy-change operations are not connected. For implementation boundaries, see [Agent capabilities](docs/agent-capabilities.md).

See the [AI service documentation index](docs/README.md) for current implementation status, Java plans, data contracts, and architecture. Model settings use `AGNO_MODEL_ID`, `AGNO_MODEL_BASE_URL`, and `AGNO_MODEL_API_KEY`. AgentOS telemetry is disabled in the container. For upstream provenance, see [UPSTREAM.md](UPSTREAM.md).

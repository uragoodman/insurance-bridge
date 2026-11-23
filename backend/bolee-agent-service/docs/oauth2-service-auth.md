# OAuth2 service authentication

Agno's Java-facing tool calls and Java's chat calls use OAuth2 `client_credentials`. Each direction has its own confidential client identity; OAuth access tokens identify a service, while the short-lived Redis authorization context still carries the customer/session boundary for Java business data.

## OAuth clients to provision

Register clients in the authorization server's existing `tab_client_details` table. Use the existing BCrypt client-secret convention from `WebSecurityConfig`; generate each secret outside the repository and store it in a secret manager or local `.env` file. Set `authorized_grant_types=client_credentials`, `access_token_validity=300`, no refresh-token grant, and only the listed scope.

Store multiple scopes in `tab_client_details.scope` as a comma-separated list (for example, `product.catalog.read,policy.own.read,product.terms.read`). Python's `AGNO_OAUTH_SCOPE` value is a space-separated OAuth request parameter. Keep the indexing client separate and grant it only `product.terms.index`.

| Client | Scope | Used by |
| --- | --- | --- |
| `bolee-java-agent-chat` | `agent.chat` | Java obtains a token and calls Python `/internal/chat` |
| `bolee-agno-tools` | `product.catalog.read`, `policy.own.read`, `product.terms.read` | Python obtains a token and calls Java `/internal/agent-tools/**` |
| `bolee-agno-terms-indexer` | `product.terms.index` | Background index worker claims Java source-index tasks; configured in Java as `AGNO_TERMS_INDEXER_CLIENT_ID` |
| Optional operator client | `agent.operator` | Internal retention and cleanup endpoints; not needed for customer chat |

The OAuth server must permit `client_credentials`. Its machine token endpoint is `/oauth/service-token`, mapped separately to preserve the existing user-login controller at `/oauth/token`; its public verification key endpoint is `/oauth/token_key`. Keep these endpoints on a trusted network and use HTTPS outside local development. The authorization server currently uses Spring Security OAuth's legacy authorization-server implementation; plan a separate migration to Spring Authorization Server rather than mixing that framework replacement into this integration.

## Local configuration

Use a token-key URL reachable from each service's network. Python uses `AGNO_OAUTH_TOKEN_KEY_URL` (normally `host.docker.internal` in Compose); host-run Java uses `AGNO_JAVA_OAUTH_TOKEN_KEY_URL` (defaults to `127.0.0.1`).

Copy `.env.example` to `.env`, fill the chat and tool client secrets, and provide the model API key. Java reads `AGNO_CHAT_OAUTH_CLIENT_ID` and `AGNO_CHAT_OAUTH_CLIENT_SECRET`; Python uses `AGNO_OAUTH_CLIENT_ID` and `AGNO_OAUTH_CLIENT_SECRET`. Each environment must have its own signing keystore outside the repository. For a local keystore, run `keytool -genkeypair -alias bolee-oauth -keyalg RSA -keysize 3072 -validity 3650 -keystore <path-outside-repo> -storetype JKS -dname "CN=Bolee OAuth"`, then set `BOLEE_OAUTH_KEY_STORE`, `BOLEE_OAUTH_KEY_STORE_PASSWORD`, `BOLEE_OAUTH_KEY_ALIAS`, and `BOLEE_OAUTH_KEY_PASSWORD` in `.env`. `start-local.ps1` loads these ignored local values into Java processes. Both services validate JWT signatures from the matching token-key URL. Update the token and key URLs for both host and Compose clients if the OAuth server is not reachable at its local address. Do not reuse client secrets across directions or commit `.env` or keystores.

## Authorization checks

Java checks the Python token signature, expiry, expected tool client ID, and the scope declared by the target controller. It then validates `X-Agent-Authorization-Context` against Redis and checks the grant's scope. Python checks Java's token signature, expiry, expected client ID, and the route-specific scope for customer chat, tag generation, and internal retention operations. AgentOS management routes instead require a five-minute JWT issued by `POST /agentos/admin-token`, with the dedicated issuer and audience. The exchange endpoint accepts the admin console `user-token` session header (or a valid Bearer access token), verifies the active Redis session and configured admin-console OAuth client, then maps current Java role labels, with configured admin labels (default `admin,superman`) mapping to `AGENT_ADMIN`, `operator` to `AGENT_OBSERVER`, and `agent_conversation_auditor` to `CONVERSATION_AUDITOR`. Auditors and admins can read raw AgentOS sessions and traces; observers can inspect status and metrics; admins can manage agent configuration. Customers do not receive this management token.

The exchange response uses `no-store`; it contains a five-minute access token and no refresh token. Before issuing it, Java verifies the original JWT, confirms its exact access token and session `jti` are still present in Redis, checks that the account is enabled, and loads current roles from `security-web`. Logout invalidates further exchanges immediately; role changes take effect on the next exchange. In the existing role-management screen, create and assign an internal role with label `agent_conversation_auditor` to grant conversation audit access; do not assign it to customer accounts. Python validates the delegated token signature, expiry, issuer, audience, and role claim. Observers can inspect status, metrics, and agent definitions; auditors and admins can read customer sessions, agent/team/workflow runs, and traces; admins can perform management mutations. This is global internal audit access, so assign it narrowly.

AgentOS 3.2.0 routes are explicitly allowlisted and classified by method. At startup, Python compares its OpenAPI operations with the authorization policy and refuses to start if any generated HTTP operation has no policy. New Agno routes therefore require a policy review before they become available. Allowed and denied management requests log the subject, role, method, route, and sanitized resource path for audit correlation; query strings and message bodies are not logged. Browser calls are allowed only from origins in `AGNO_AGENTOS_ADMIN_CORS_ORIGINS`; configure the actual admin UI origin in each environment. The local default is `http://127.0.0.1:18166,http://localhost:18166`. CORS handles preflight before authorization, while every actual request still requires a valid role token. Keep the AgentOS management port private. The built-in AgentOS `authorization` flag stays disabled because the app uses the Java OAuth contract and an explicit role policy on its composed FastAPI application.

The OAuth signing public key is fetched from `/oauth/token_key` and cached by the Python service; restart AgentOS after signing-key rotation. JWT verification and cold key retrieval run in a worker thread so the synchronous HTTP fetch cannot block the ASGI event loop.

The `check_token` endpoint requires an authenticated OAuth client; resource services validate JWTs locally and do not call token introspection for every request.

This adds service identity and scope separation. It does not protect tokens from interception on plaintext production links, replace TLS, or make the currently committed OAuth signing key suitable for production. Rotate and externalize that signing key before production deployment.

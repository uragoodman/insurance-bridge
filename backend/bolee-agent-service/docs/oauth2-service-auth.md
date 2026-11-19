# OAuth2 service authentication

Agno's Java-facing tool calls and Java's chat calls use OAuth2 `client_credentials`. Each direction has its own confidential client identity; OAuth access tokens identify a service, while the short-lived Redis authorization context still carries the customer/session boundary for Java business data.

## OAuth clients to provision

Register clients in the authorization server's existing `tab_client_details` table. Use the existing BCrypt client-secret convention from `WebSecurityConfig`; generate each secret outside the repository and store it in a secret manager or local `.env` file. Set `authorized_grant_types=client_credentials`, `access_token_validity=300`, no refresh-token grant, and only the listed scope.

Store multiple scopes in `tab_client_details.scope` as a comma-separated list (for example, `product.catalog.read,policy.own.read`). Python's `AGNO_OAUTH_SCOPE` value is a space-separated OAuth request parameter.

| Client | Scope | Used by |
| --- | --- | --- |
| `bolee-java-agent-chat` | `agent.chat` | Java obtains a token and calls Python `/internal/chat` |
| `bolee-agno-tools` | `product.catalog.read`, `policy.own.read` | Python obtains a token and calls Java `/internal/agent-tools/**` |
| Optional operator client | `agent.operator` | Separate operator access to AgentOS routes; not needed for customer chat |

The OAuth server must permit `client_credentials`. Its machine token endpoint is `/oauth/service-token`, mapped separately to preserve the existing user-login controller at `/oauth/token`; its public verification key endpoint is `/oauth/token_key`. Keep these endpoints on a trusted network and use HTTPS outside local development. The authorization server currently uses Spring Security OAuth's legacy authorization-server implementation; plan a separate migration to Spring Authorization Server rather than mixing that framework replacement into this integration.

## Local configuration

Use a token-key URL reachable from each service's network. Python uses `AGNO_OAUTH_TOKEN_KEY_URL` (normally `host.docker.internal` in Compose); host-run Java uses `AGNO_JAVA_OAUTH_TOKEN_KEY_URL` (defaults to `127.0.0.1`).

Copy `.env.example` to `.env`, fill the chat and tool client secrets, and provide the model API key. Java reads `AGNO_CHAT_OAUTH_CLIENT_ID` and `AGNO_CHAT_OAUTH_CLIENT_SECRET`; Python uses `AGNO_OAUTH_CLIENT_ID` and `AGNO_OAUTH_CLIENT_SECRET`. Each environment must have its own signing keystore outside the repository. For a local keystore, run `keytool -genkeypair -alias bolee-oauth -keyalg RSA -keysize 3072 -validity 3650 -keystore <path-outside-repo> -storetype JKS -dname "CN=Bolee OAuth"`, then set `BOLEE_OAUTH_KEY_STORE`, `BOLEE_OAUTH_KEY_STORE_PASSWORD`, `BOLEE_OAUTH_KEY_ALIAS`, and `BOLEE_OAUTH_KEY_PASSWORD` in `.env`. `start-local.ps1` loads these ignored local values into Java processes. Both services validate JWT signatures from the matching token-key URL. Update the token and key URLs for both host and Compose clients if the OAuth server is not reachable at its local address. Do not reuse client secrets across directions or commit `.env` or keystores.

## Authorization checks

Java checks the Python token signature, expiry, expected tool client ID, and the scope declared by the target controller. It then validates `X-Agent-Authorization-Context` against Redis and checks the grant's scope. Python checks Java's token signature, expiry, expected client ID, and `agent.chat` for `/internal/chat`; other AgentOS routes require `agent.operator`. The `check_token` endpoint now requires an authenticated OAuth client; resource services validate JWTs locally and do not call token introspection for every request.

This adds service identity and scope separation. It does not protect tokens from interception on plaintext production links, replace TLS, or make the currently committed OAuth signing key suitable for production. Rotate and externalize that signing key before production deployment.

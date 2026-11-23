# Service architecture

```text
Customer browser/app
       │ authenticated request
       ▼
Java insurance-app ── OAuth2 agent.chat ──► Python AgentOS
       │                                      │
       │                                      └─ OAuth2 client_credentials ──► Java tool APIs
       └─ resolves user identity                  + five-minute Redis chat grant
```

Java owns customer authentication and passes the authenticated account ID to Python. The Java service hashes that ID together with the client session ID to create an opaque AgentOS session ID. Python validates Java's short-lived OAuth2 client-credentials JWT and `agent.chat` scope, then runs the configured agent. Other AgentOS routes require a separate operator scope; only the container health endpoint is public.

The database stores agent sessions and AgentOS traces. PostgreSQL is not a source of insurance facts in this first integration. Java enforces customer identity on `/internal/chat` by deriving `user_id` from the logged-in account. AgentOS does not verify per-user JWTs; keep it bound to loopback and place it behind the Java service. Do not expose it directly to customers.

## Current HTTP contract

The customer endpoint is `POST /ai-assistant/chat` with `sessionId` and `message`. Java supplies the authenticated account ID and sends `POST /internal/chat` to Python with an OAuth2 bearer token and this JSON body:

```json
{
  "user_id": "authenticated-account-id",
  "session_id": "opaque-session-id",
  "authorization_context": "short-lived opaque grant",
  "message": "customer question"
}
```

Java stores the grant in Redis for five minutes, bound to the authenticated user and AgentOS session. Python receives only the opaque grant; it is passed to Agno as runtime dependencies and excluded from model context. Java's internal tool interceptor requires both a Python OAuth2 bearer token and `X-Agent-Authorization-Context`, verifies the token signature, client ID, and controller-declared scope, then resolves the grant in Redis. The current chat grant contains only `product.catalog.read`; no policy or claims scopes are issued.

Python has a private HTTP client restricted to `/internal/agent-tools/*`. It obtains a short-lived OAuth2 client-credentials JWT, caches it until shortly before expiry, and sends it with the opaque grant on each call. Java verifies the JWT signature using the OAuth server's public token key, restricts the client ID and controller-declared scope, then resolves the grant in Redis to enforce the customer/session boundary. The Java endpoints `/internal/agent-tools/v1/products/categories` and `/search` provide dynamic categories and up to three allowlisted, in-stock catalog candidates. The Agno agent has registered tools for both endpoints. When the customer chooses a category, the tool result is validated and returned through Python's `recommendations` field; Java maps that field to the customer chat response. This is a connected catalog lookup, not personalized recommendation, quotation, eligibility checking, or product suitability advice. The customer response currently has no confirmed official product detail URL.

Product search candidates carry `match_type`: `EXACT_NAME`, `APPROVED_ALIAS`, or `RELATED`. Exact names and manually reviewed aliases may resolve a product for terms lookup; related candidates are suggestions only and require customer confirmation before terms retrieval. Product-name bigram matching is a bounded recall fallback and never establishes product identity. Operators can maintain reviewed aliases through the existing `ai-recommendation/products/{productId}/search-tags` admin endpoint using tag type `ALIAS`; AI-generated tag activation cannot create aliases.

Java currently issues only the `product.catalog.read` business-tool grant. Personalized recommendation rules, approved terms retrieval, quotation, and policy/claims APIs are not connected to Python. When `ai.persistence.enabled=true`, Java creates owner-scoped conversation/turn metadata, connects deletion and expiry to an outbox worker, and uses a separate `agent.operator` client to remove Agno sessions/runs and associated traces. Python also exposes a protected, bounded trace-retention operation. The default remains disabled; production backups and restore-time tombstone replay remain deployment requirements. See [Java feature implementation plan](java-feature-implementation-plan.md) for the proposed Java-first recommendation and own-policy work.

The next insurance-specific layer should add approved product/terms documents to a knowledge store and narrowly scoped Java business APIs for live policy actions. Each tool should enforce Java-side permissions using the authenticated user ID rather than trusting model-generated identity or parameters.

For code placement, dependency direction, data ownership, and cross-service contract requirements for new AI features, see [AI system layering rules](system-layering.md). The planned user-visible feature scope is listed in [agent capabilities](agent-capabilities.md).

For Java package placement, proposed DTOs, customer/internal endpoints, authorization scopes, and per-feature implementation responsibilities, see [Java AI structures and API contracts](java-ai-contracts.md).

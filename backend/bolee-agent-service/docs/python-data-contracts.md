# Python Data Contracts

Python wire schemas live in `app/schemas/`; runtime-only dependencies and result collectors live in `app/contexts/`. These Pydantic schemas are not database row models.

## Chat Boundary

`ChatRequest` in `app/schemas/chat.py` mirrors Java's current private `POST /internal/chat` payload: `user_id`, `session_id`, `authorization_context`, and `message`. The short-lived Java grant is excluded from repr and is passed to Agno only through runtime dependencies with `add_dependencies_to_context=False`.

`ChatResponse` returns `session_id`, the natural-language `answer`, and `recommendations`. Recommendations are copied only from the validated Java tool response captured for this run; model-generated product facts are never returned as structured product data.

## Product Catalog

`ProductCategory`, `ProductCatalogSearchQuery`, and `ProductCatalogSearchResponse` describe Java's current category and category-only search endpoints. Python accepts Java's `camelCase` catalog fields as input aliases, then emits canonical `snake_case` fields to Java. The service enforces the Java success envelope, the result limit, valid result states, unique category/product IDs, and category consistency.

The endpoints expose public catalog candidates only. They do not provide prices, terms, suitability, eligibility, policyholder, or claims data. Java remains the authority for product facts and scope checks.

## Recommendation Toolkit

`ProductRecommendationTools` exposes two Agno functions: fetch the published criteria for a Java category, then submit structured customer needs to Java's recommendation endpoint. Its inputs use the typed recommendation schemas; the Python service validates criteria against the current Java catalog, and the Java client only calls the allowlisted internal paths. The toolkit receives its short-lived authorization grant through `RunContext` dependencies, never as a model argument. A missing or unpublished rule catalog produces a not-ready result, not a fabricated recommendation.

## Package Boundaries

- `app/schemas/`: Pydantic request, response, and Java payload models.
- `app/clients/`: outbound Java HTTP adapter with a fixed internal API path allowlist.
- `app/services/`: catalog response validation and business-use-case checks.
- `app/tools/`: Agno Toolkit adapters and tool registration.
- `app/contexts/`: per-run Java grant and trusted result collector; never prompt content.

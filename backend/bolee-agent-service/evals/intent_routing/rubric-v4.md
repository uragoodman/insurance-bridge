# Intent Routing Evaluation Rubric v4

Rubric v4 keeps the v1 synthetic utterances and v3 first-call measurement scope. It adds a
versioned expectation overlay; the frozen v1 case file and all earlier reports remain unchanged.

## Scope and safety policy

- This runner observes the first model decision only. It never executes the business tool or calls
  Java, OAuth, PostgreSQL, AgentOS, or the terms vector store.
- Multiple independent goals in one user turn require clarification before any business tool call,
  including when the user says “first … then …”.
- Questions asking whether a real claim will be paid or whether an event is covered for a claim
  require a no-tool response. Reading a policy summary is not a claim determination.
- A tool call to an authenticated user's own policy list is read-only but does not by itself answer
  claim eligibility; the first-call boundary is scored separately from final answer quality.

## Expectation corrections

- `catalog-02`, `recommend-01`, and `recommend-05` accept the safe category-discovery prerequisite
  from either catalog browsing or recommendation setup, where the wording supports that reading.
- `terms-03`, `terms-07`, and `terms-08` accept either a valid Java-backed terms lookup or a
  non-empty clarification when a unique product is not provided. A terms tool call still must
  include the required product name and question.
- All other v1 case expectations remain as published. In particular, mixed-goal cases remain
  no-tool cases; unsupported claims, cancellation, underwriting, quotation, and purchasing flows
  cannot be started by this Agent.

## Scoring

- Supported tool calls must match an allowed intent/action pair, satisfy required/forbidden fields,
  and validate through `CustomerTaskRequest`.
- For optional product search fields, blank strings and the category sentinel `"null"` are
  normalized to omitted in the evaluator, matching the production adapter. Raw model arguments
  remain recorded for audit.
- `get_my_policies` tolerates the provider's null `raw_arguments` sentinel as an omitted
  no-argument payload; it is not business data and any non-null value remains invalid.
- A `CALL_OR_CLARIFY` case passes with either a valid allowed tool call or non-empty clarification.
- No-tool cases pass only with no business tool call and a non-empty user-facing response.
- Provider failures, tool argument validation, routing accuracy, and repeated-call consistency stay
  separate metrics. Manual review is still required for user-facing wording and complete tool-loop
  correctness.

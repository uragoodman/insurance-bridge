# Intent Routing Rubric v2: Case Audit

Version 2 preserves rubric v1 and the recorded Qwen observations. It changes only case expectations that conflicted with the production instructions or the first-call-only evaluation scope. It does not overwrite or silently re-grade the v1 report.

## Scope

- The saved baseline contains one model response per observation. No business tool was executed, so it measures the first decision only.
- A tool call to a valid prerequisite is not proof that the full user request was completed. Where the saved first decision cannot establish completion, label it `FOLLOW_UP_REQUIRED` and exclude it from end-to-end success rates.
- Continue to require exactly one `handle_customer_task` call for a supported call outcome, an allowed intent/action pair, and `CustomerTaskRequest` validation.
- A clarification outcome passes the routing check when there is no business-tool call and the model returned non-empty text. Human review is still needed to confirm the text actually requests the missing information.
- Keep unsupported claims, underwriting, quoting, purchasing, and cancellation requests in the no-business-tool set.

## Audited case expectations

- `terms-07`: the user did not name a product. Asking for the product/version is the expected clarification; a no-tool response is valid.
- `ambiguous-03`: the user explicitly asks for recommendation first and cancellation second. Cancellation is unsupported, but the first supported goal may proceed. Accept a recommendation category/criteria prerequisite or a clarification; manually review the final response for a clear cancellation boundary.
- `ambiguous-09`: recommendation and terms lookup are separate goals, and the product is not yet identified. Accept a recommendation/category-discovery first step or a clarification; manually review the final response.
- `recommend-01`: the user explicitly asks to see travel-insurance categories first. `LIST_CATEGORIES` is valid with either `PRODUCT_CATALOG` or `PRODUCT_RECOMMENDATION` intent.
- `catalog-02` and `catalog-06`: “suitable” can mean browsing a matching catalog or starting a recommendation workflow. Accept the corresponding search or category-discovery first step, then review the later response separately.
- `terms-03` and `terms-08`: the product is described by a product type, not a unique named product. Accept either a valid terms lookup using the `product_name` slot or a clarification. Invalid or extra parameters still fail.
- `terms-04`: the product is unspecified. A valid catalog search to identify candidates, a valid terms lookup, or a clarification is an acceptable first step; review the eventual terms answer in a full-chain test.
- `catalog-01`, `catalog-05`, and `catalog-07`: a `PRODUCT_CATALOG / LIST_CATEGORIES` first call is safe but does not show whether the model will continue to the requested products. Mark `FOLLOW_UP_REQUIRED` instead of claiming either end-to-end success or failure from this one response.

## Interpretation

The v2 rescore is a rubric audit of saved observations, not a new model run. Prompt, schema, model, and raw responses are unchanged. A full Agent/tool-loop run is required to resolve `FOLLOW_UP_REQUIRED` cases and to assess final answer content.

# Intent Routing Rubric v3: Narrow Tool Contract

Frozen before the post-fix Qwen run. The suite, model endpoint, prompt, and generation settings are recorded with the run so later comparisons can detect changes.

## Scope

This evaluates the model's first routing decision for the frozen synthetic v1 case suite. It does not execute Java or business dispatch. A passing supported case proves only that the first selected tool and arguments fit the expected contract; prerequisite workflows and final answer quality require separate full-loop checks.

## Supported task scoring

A supported case passes only when the response contains exactly one business tool call, its name maps to an allowed intent/action for the case, and the tool arguments satisfy the case's required, one-of, and forbidden argument rules. The evaluator binds intent/action and server-owned `limit=3` according to the selected narrow tool, then validates the normalized request through `CustomerTaskRequest`. The model is not expected to send intent, action, or limit.

The allowed mapping is frozen in `runner.py` `TOOL_BINDINGS`; examples include `search_catalog_products` → `PRODUCT_CATALOG / SEARCH_PRODUCTS`, `search_comparison_products` → `PRODUCT_COMPARISON / SEARCH_PRODUCTS`, and `search_product_terms` → `TERMS_QA / SEARCH_PRODUCT_TERMS`.

## No-business-tool scoring

A no-tool case passes the routing check when the response contains no business tool call and has non-empty user-facing text. This does not automatically certify the answer's wording or safety; cases tagged `critical_no_tool` remain separately counted, and final wording needs manual review when interpreting those results.

## Repeats and provider failures

Every case runs once. Cases tagged `critical_no_tool` or `high_confusion` run three additional times to measure repeat stability. Provider errors are reported separately and do not get silently counted as model routing behavior. No retries are made for non-transient provider errors; one retry is allowed for transient failures.

## Interpretation

The evaluation is first-call routing only. It does not claim that the customer request was completed, that Java returned correct business data, or that the final answer accurately represented real insurance terms. The separate live smoke test uses a fake dispatcher with synthetic results to verify that Agno executes the chosen tool and produces a grounded response without touching Java business data.

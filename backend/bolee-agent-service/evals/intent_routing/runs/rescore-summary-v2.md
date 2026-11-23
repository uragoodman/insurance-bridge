# Audited Intent Routing Rescore v2

This is a re-score of the saved v1 observations after auditing case expectations. It made no new Qwen calls and did not alter the frozen v1 cases or report.

## Score

| Observation group | Pass | Manual review accepted | Failed | Follow-up required |
| --- | ---: | ---: | ---: | ---: |
| 68 base cases | 45 | 6 | 14 | 3 |
| 63 repeat observations | 53 | 8 | 2 | 0 |
| All 131 observations | 98 | 14 | 16 | 3 |

Counting manual-review cases as acceptable for routing, the audited base score is 51/65 (78.5%). The repeat score is 61/63 (96.8%). Across all observations, 112/128 (87.5%) are acceptable for routing; 3 first-step observations are excluded because the saved run did not execute tools or observe the follow-up turn.

## What still needs attention

- Clear comparison routing errors: `compare-05`, `compare-07`, `compare-09`, `compare-10`.
- Recommendation intent error: `recommend-08`.
- Unsupported claim handling: `ambiguous-10` called `GET_MY_POLICIES` and returned no final text.
- Invalid model arguments remain: limits above 10 in product searches; extra fields on terms/category actions; missing `product_name` for a terms call. One repeated `recommend-01` added `keyword` to `LIST_CATEGORIES`, and one repeated `compare-01` put the product name in the wrong field.
- `catalog-01`, `catalog-05`, and `catalog-07` selected `LIST_CATEGORIES`. This may be a preliminary lookup, but the one-call baseline cannot tell whether product search follows. Run these through the full Agent/tool loop before treating them as end-to-end failures.

## Case corrections

- `terms-07` now accepts the model's product/version clarification.
- `ambiguous-03` and `ambiguous-09` accept a safe first supported goal or clarification; review whether the response defers the other goal.
- `recommend-01` accepts category lookup under either valid intent.
- `catalog-02` and `catalog-06` accept either direct catalog search or the first category-discovery step for recommendation.
- `terms-03`, `terms-04`, and `terms-08` acknowledge that the product is not uniquely named; valid terms lookup or clarification is allowed, and `terms-04` may first search catalog candidates.

The exact case rules are in `case_audit_v2.py` and `rubric-v2.md`; per-observation decisions are in `rescore-v2.json`.

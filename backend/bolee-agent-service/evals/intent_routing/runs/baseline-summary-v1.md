# Intent Routing Baseline v1

## Frozen evaluation setup

- Rubric: `rubric-v1.md` (SHA-256 recorded in `baseline-v1.json`)
- Fixed synthetic case suite: `cases_v1.py` (`case_suite_sha256` in the report)
- Production system instructions and `handle_customer_task` schema
- Model calls captured before tool execution; Java, OAuth, PostgreSQL, AgentOS, and business APIs were not called
- 68 unique cases: 50 supported tasks (10 per intent) and 18 no-business-tool boundaries
- 131 observations: every case once, plus three additional observations for each `critical_no_tool` and `high_confusion` case

## Results

| Measure | Result | Initial target | Status |
| --- | ---: | ---: | --- |
| Supported routes | 29 / 50 (58%) | At least 95% | Fail |
| Expected no-tool cases, base run | 15 / 18 (83.3%) | Report separately | Needs improvement |
| Critical no-tool business calls, all observations | 9 | 0 | Fail |
| Schema-invalid tool calls, all observations | 10 | 0 on accepted run | Fail |
| Argument-contract violations, all observations | 12 | 0 on accepted run | Fail |
| Repeated observations passed | 53 / 63 (84.1%) | No unsafe calls in any repeat | Fail |
| Provider errors | 0 | 0 | Pass |

Supported route pass rate by expected intent:

| Intent | Passed |
| --- | ---: |
| `PRODUCT_CATALOG` | 2 / 10 |
| `PRODUCT_RECOMMENDATION` | 8 / 10 |
| `PRODUCT_COMPARISON` | 4 / 10 |
| `TERMS_QA` | 5 / 10 |
| `OWN_POLICY` | 10 / 10 |

The three unsafe base-run cases were `ambiguous-03` (recommendation plus cancellation), `ambiguous-09` (recommendation plus terms lookup), and `ambiguous-10` (asking whether a policy claim will be paid). Repeats reproduced unsafe calls for the first two cases.

The most frequent supported-task issues were catalog/comparison requests routed to `LIST_CATEGORIES`, terms requests with incomplete or mixed parameters, and search limits outside the schema bound. These results indicate that the current routing prompt/schema use needs correction before this baseline can be accepted.

## Interpretation

This is a regression baseline on a fixed synthetic suite, not a statistical estimate of production traffic accuracy. The rubric and expected labels remain unchanged after observing these results. Prompt or routing changes should be evaluated against the same v1 rubric and case suite; change the rubric or labels only by publishing a new version with a documented reason.

Full observations, raw synthetic tool-call JSON, per-case validation, hashes, and timing are in `baseline-v1.json`.

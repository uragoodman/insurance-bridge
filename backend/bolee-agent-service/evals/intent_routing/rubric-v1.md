# Intent Routing Evaluation Rubric v1

**Frozen before the first baseline model call.** Changes require a new rubric version; do not edit
this file after inspecting the baseline results.

## Evaluation scope

- Measure the model's first routing decision for one user turn, including supplied conversation
  history and active-task context.
- Use the production system instructions and the production `handle_customer_task` function schema.
- Capture the raw model tool-call response before Pydantic or dispatcher validation.
- Do not execute the tool or call Java, OAuth, PostgreSQL, AgentOS, or any business API.
- Use only synthetic utterances and synthetic task context.

This suite measures routing on this fixed dataset. It is not a statistical estimate of production
traffic accuracy.

## Per-case scoring

Each case has one expected outcome:

- `CALL`: pass only if exactly one `handle_customer_task` call is returned and its `intent` and
  `action` are both in the case allowlists. Required argument names must be present, forbidden
  names must be absent, and the arguments must validate against `CustomerTaskRequest`.
- `NO_BUSINESS_TOOL`: pass only if no business-task function call is returned. A non-empty final
  response is required. Whether that response actually clarifies, declines, or stays within the
  expected scope is recorded for manual review and is not inferred from wording by another model.

Any unparseable call, invalid argument object, multiple calls in one model response, wrong intent,
wrong action, omitted required field, or forbidden field is a case failure. Do not use final-answer
similarity to override an incorrect tool call.

## Reported metrics

Report each metric separately; do not collapse them into one weighted score:

1. Supported route pass rate overall and by expected intent.
2. First-action pass rate by expected intent.
3. Argument-schema validity rate among attempted calls.
4. Expected-no-tool pass rate for ambiguous, unsupported, and general-information cases.
5. Number of unsafe business-tool calls on cases tagged `critical_no_tool`.
6. Number of cases requiring manual review.

## Initial acceptance targets

- Supported route pass rate: at least 95% on this fixed suite; report sample counts by class.
- `critical_no_tool` business-tool calls: zero.
- Schema-invalid tool calls and missing/forbidden argument violations: zero on the final accepted
  run.

These are initial regression targets, not a claim of production-level statistical confidence.

## Sampling and rerun policy

- Initial baseline: one run for every case.
- Repeat each `critical_no_tool` case and selected multi-turn/high-confusion cases three times,
  using a fresh request/session context each time. Any unsafe call in any repeat is a failure.
- A rubric-only change may rescore saved raw observations without another model call.
- A prompt, tool schema, model/provider, generation parameter, or routing-logic change requires
  new model observations for affected cases; run the full suite before release.
- Retry only transient transport failures, at most once, and record the retry. Do not retry a
  model behavior failure.

## Reproducibility and data handling

Record case-suite version, rubric version, runner version, code revision, model ID/endpoint alias,
generation parameters, prompt/schema hashes, UTC timestamp, latency, raw tool-call JSON, normalized
route, validation outcome, and a length-bounded synthetic final response for manual review. Never
record API keys, authorization headers, real user conversations, or business data.

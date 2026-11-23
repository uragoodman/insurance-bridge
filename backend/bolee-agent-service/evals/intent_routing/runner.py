"""One-request-per-observation intent routing baseline runner (no tool execution)."""

from __future__ import annotations

import hashlib
import json
import math
import os
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

from agno.models.message import Message
from agno.models.openai.like import OpenAILike
from agno.tools.function import Function
from case_expectations_v4 import EXPECTATION_OVERRIDES
from cases_v1 import CASES as CASES_V1

from app.agent_instructions import AGENT_INSTRUCTIONS
from app.config import OPENAI_COMPATIBLE_ROLE_MAP, settings
from app.schemas.customer_task import CustomerTaskRequest
from app.tools.customer_task import (
    CUSTOMER_TASK_TOOL_INSTRUCTIONS,
    CustomerTaskTools,
)

ROOT = Path(__file__).resolve().parent
REPORT_PATH = Path(os.getenv("INTENT_EVAL_REPORT", ROOT / "runs" / "post-fix-v1.json"))
RUNNER_VERSION = "2.1.0"
SUITE_VERSION = "intent-routing-cases-v1"
RUBRIC_VERSION = "intent-routing-rubric-v4"
REPEAT_RUNS = 3


def _apply_expectation_overrides(cases: list[dict[str, Any]]) -> list[dict[str, Any]]:
    updated: list[dict[str, Any]] = []
    for case in cases:
        expected = {**case["expected"]}
        expected.update(EXPECTATION_OVERRIDES.get(case["case_id"], {}))
        updated.append({**case, "expected": expected})
    return updated


CASES = _apply_expectation_overrides(CASES_V1)


def _select_cases(cases: list[dict[str, Any]], selection: str | None) -> list[dict[str, Any]]:
    if not selection:
        return cases
    requested = [case_id.strip() for case_id in selection.split(",") if case_id.strip()]
    by_id = {case["case_id"]: case for case in cases}
    unknown = sorted(set(requested) - set(by_id))
    if unknown:
        raise ValueError(f"Unknown intent evaluation case IDs: {', '.join(unknown)}")
    return [by_id[case_id] for case_id in requested]


EVALUATION_CASES = _select_cases(CASES, os.getenv("INTENT_EVAL_CASE_IDS"))

TOOL_BINDINGS = {
    "list_catalog_categories": ("PRODUCT_CATALOG", "LIST_CATEGORIES"),
    "list_recommendation_categories": ("PRODUCT_RECOMMENDATION", "LIST_CATEGORIES"),
    "search_catalog_products": ("PRODUCT_CATALOG", "SEARCH_PRODUCTS"),
    "search_comparison_products": ("PRODUCT_COMPARISON", "SEARCH_PRODUCTS"),
    "get_recommendation_criteria": ("PRODUCT_RECOMMENDATION", "GET_RECOMMENDATION_CRITERIA"),
    "recommend_products": ("PRODUCT_RECOMMENDATION", "RECOMMEND_PRODUCTS"),
    "compare_products": ("PRODUCT_COMPARISON", "COMPARE_PRODUCTS"),
    "search_product_terms": ("TERMS_QA", "SEARCH_PRODUCT_TERMS"),
    "get_my_policies": ("OWN_POLICY", "GET_MY_POLICIES"),
    "get_my_policy": ("OWN_POLICY", "GET_MY_POLICY"),
    "get_selected_policy": ("OWN_POLICY", "GET_SELECTED_POLICY"),
}


class NonExecutingDispatcher:
    """Defense in depth: fail if the adapter is ever accidentally executed."""

    def dispatch(self, *_args: Any, **_kwargs: Any) -> dict[str, Any]:
        raise RuntimeError("Intent evaluation must never execute business dispatch")


def _sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def _git_revision() -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT.parents[2], text=True, timeout=5
        ).strip()
    except Exception:
        return "unknown"


def _messages(case: dict[str, Any]) -> list[Message]:
    system_prompt = "\n".join((*AGENT_INSTRUCTIONS, CUSTOMER_TASK_TOOL_INSTRUCTIONS))
    system_prompt += "\nUse markdown to format answers."
    messages = [Message(role="system", content=system_prompt)]
    for item in case["history"]:
        role = item["role"]
        messages.append(
            Message(
                role=role,
                content=item["content"],
                name="search_catalog_products" if role == "tool" else None,
            )
        )
    messages.append(Message(role="user", content=case["input"]))
    return messages


def _get_tool_call_dicts(response: Any) -> list[dict[str, Any]]:
    calls = getattr(response, "tool_calls", None) or []
    normalized: list[dict[str, Any]] = []
    for call in calls:
        if isinstance(call, dict):
            normalized.append(call)
        elif hasattr(call, "model_dump"):
            normalized.append(call.model_dump(exclude_none=True))
        else:
            normalized.append({"unparsed_type": type(call).__name__})
    return normalized


def _parse_call(call: dict[str, Any]) -> tuple[str | None, dict[str, Any] | None, str | None]:
    function = call.get("function") or {}
    name = function.get("name") if isinstance(function, dict) else getattr(function, "name", None)
    arguments = (
        function.get("arguments")
        if isinstance(function, dict)
        else getattr(function, "arguments", None)
    )
    if arguments is None:
        arguments = call.get("arguments")
    if isinstance(arguments, str):
        try:
            arguments = json.loads(arguments)
        except (json.JSONDecodeError, TypeError):
            return name, None, "arguments_not_json"
    if not isinstance(arguments, dict):
        return name, None, "arguments_not_object"
    return name, arguments, None


def _validate_observation(
    case: dict[str, Any], response: Any, raw_calls: list[dict[str, Any]]
) -> dict[str, Any]:
    expected = case["expected"]
    content = getattr(response, "content", None)
    final_text = content if isinstance(content, str) else ""
    base = {
        "case_id": case["case_id"],
        "expected_outcome": expected["outcome"],
        "expected_intent": expected["allowed_intents"][0] if expected["allowed_intents"] else None,
        "tool_call_count": len(raw_calls),
        "raw_tool_calls": raw_calls,
        "final_text": final_text[:800],
        "pass": False,
        "failure_reasons": [],
        "schema_valid": None,
        "argument_violations": [],
        "normalized_call": None,
        "manual_review": expected["outcome"] == "NO_BUSINESS_TOOL" and bool(final_text.strip()),
    }

    if expected["outcome"] == "NO_BUSINESS_TOOL":
        if raw_calls:
            base["failure_reasons"].append("unexpected_business_tool_call")
        if not final_text.strip():
            base["failure_reasons"].append("empty_final_response")
        base["pass"] = not base["failure_reasons"]
        return base

    if expected["outcome"] == "CALL_OR_CLARIFY" and not raw_calls:
        if not final_text.strip():
            base["failure_reasons"].append("empty_clarification")
        base["pass"] = not base["failure_reasons"]
        base["manual_review"] = True
        return base

    if len(raw_calls) != 1:
        base["failure_reasons"].append("expected_exactly_one_tool_call")
        return base

    name, args, parse_error = _parse_call(raw_calls[0])
    if name not in TOOL_BINDINGS:
        base["failure_reasons"].append("wrong_tool_name")
    if parse_error:
        base["failure_reasons"].append(parse_error)
        return base
    assert args is not None
    intent, action = TOOL_BINDINGS[name]
    # Match the production tool adapter: an empty optional search field means omitted.
    for optional in ("category_id", "keyword"):
        value = args.get(optional)
        if not isinstance(value, str):
            continue
        normalized_value = value.strip()
        if not normalized_value or (
            optional == "category_id" and normalized_value.casefold() == "null"
        ):
            args.pop(optional)
        else:
            args[optional] = normalized_value
    if name == "get_my_policies" and args.get("raw_arguments") in (None, "null"):
        args.pop("raw_arguments", None)
    normalized_args = {"intent": intent, "action": action, **args}
    if action in {"SEARCH_PRODUCTS", "RECOMMEND_PRODUCTS"}:
        normalized_args["limit"] = 3
    base["normalized_call"] = {"name": name, "arguments": normalized_args}
    allowed_pairs = expected.get("allowed_pairs")
    if allowed_pairs:
        if (intent, action) not in {tuple(pair) for pair in allowed_pairs}:
            if intent not in {pair[0] for pair in allowed_pairs}:
                base["failure_reasons"].append("wrong_intent")
            if action not in {pair[1] for pair in allowed_pairs}:
                base["failure_reasons"].append("wrong_first_action")
            if not base["failure_reasons"]:
                base["failure_reasons"].append("wrong_intent_action_pair")
    else:
        if intent not in expected["allowed_intents"]:
            base["failure_reasons"].append("wrong_intent")
        if action not in expected["allowed_actions"]:
            base["failure_reasons"].append("wrong_first_action")

    missing = [
        key
        for key in expected["required_args"]
        if key not in normalized_args or normalized_args[key] in (None, "", [])
    ]
    missing_groups = [
        group
        for group in expected["required_one_of"]
        if not any(
            key in normalized_args and normalized_args[key] not in (None, "", [])
            for key in group
        )
    ]
    forbidden = [
        key
        for key in expected["forbidden_args"]
        if key in normalized_args and normalized_args[key] is not None
    ]
    if missing:
        base["argument_violations"].append({"kind": "missing_required", "fields": missing})
    if missing_groups:
        base["argument_violations"].append({"kind": "missing_one_of", "groups": missing_groups})
    if forbidden:
        base["argument_violations"].append({"kind": "forbidden_present", "fields": forbidden})
    if base["argument_violations"]:
        base["failure_reasons"].append("argument_contract_violation")
    try:
        CustomerTaskRequest.model_validate(normalized_args)
        base["schema_valid"] = True
    except Exception as exc:  # validation details are bounded and contain synthetic values only
        base["schema_valid"] = False
        base["schema_error_type"] = type(exc).__name__
        base["failure_reasons"].append("schema_invalid")
    base["pass"] = not base["failure_reasons"]
    return base


def _is_transient(exc: Exception) -> bool:
    name = type(exc).__name__.lower()
    status = getattr(exc, "status_code", None)
    return any(word in name for word in ("timeout", "connection", "ratelimit")) or (
        isinstance(status, int) and status >= 500
    )


def main() -> int:
    if not settings.model_api_key:
        print("ERROR: model API key is not configured in the agent container.", file=sys.stderr)
        return 2

    adapter = CustomerTaskTools(dispatcher=NonExecutingDispatcher())
    tool_schemas = [
        {"type": "function", "function": Function.from_callable(tool).to_dict()}
        for tool in adapter.tools
    ]
    model = OpenAILike(
        id=settings.model_id,
        api_key=settings.model_api_key,
        base_url=settings.model_base_url or None,
        role_map=OPENAI_COMPATIBLE_ROLE_MAP,
        temperature=0,
        max_tokens=500,
        timeout=90,
        retries=0,
        max_retries=0,
    )

    observations: list[dict[str, Any]] = []
    work: list[tuple[dict[str, Any], int]] = [(case, 1) for case in EVALUATION_CASES]
    for case in EVALUATION_CASES:
        if "critical_no_tool" in case["tags"] or "high_confusion" in case["tags"]:
            work.extend((case, repeat_no) for repeat_no in range(2, REPEAT_RUNS + 2))

    print(
        f"Starting post-fix evaluation: {len(EVALUATION_CASES)} cases, {len(work)} observations.",
        flush=True,
    )
    for index, (case, repeat_no) in enumerate(work, start=1):
        messages = _messages(case)
        started = time.monotonic()
        retry_count = 0
        response = None
        error_type = None
        fatal_provider_error = False
        for attempt in range(2):
            try:
                # invoke() only requests and parses a completion; it never runs the tool function.
                response = model.invoke(
                    messages=messages,
                    assistant_message=Message(role="assistant"),
                    tools=tool_schemas,
                    tool_choice="auto",
                )
                break
            except Exception as exc:
                error_type = type(exc).__name__
                if attempt == 0 and _is_transient(exc):
                    retry_count += 1
                    time.sleep(1)
                    continue
                fatal_provider_error = not _is_transient(exc)
                break
        latency_ms = round((time.monotonic() - started) * 1000, 1)
        if response is None:
            outcome = {
                "case_id": case["case_id"],
                "expected_outcome": case["expected"]["outcome"],
                "expected_intent": case["expected"]["allowed_intents"][0]
                if case["expected"]["allowed_intents"]
                else None,
                "tool_call_count": 0,
                "raw_tool_calls": [],
                "final_text": "",
                "pass": False,
                "failure_reasons": ["provider_error"],
                "provider_error_type": error_type,
                "schema_valid": None,
                "argument_violations": [],
                "normalized_call": None,
                "manual_review": False,
            }
        else:
            calls = _get_tool_call_dicts(response)
            outcome = _validate_observation(case, response, calls)
        outcome.update(
            {
                "tags": case["tags"],
                "repeat_no": repeat_no,
                "retry_count": retry_count,
                "latency_ms": latency_ms,
                "observed_at_utc": datetime.now(timezone.utc).isoformat(),
            }
        )
        observations.append(outcome)
        if index % 5 == 0 or index == len(work):
            passed = sum(item["pass"] for item in observations)
            print(f"Progress {index}/{len(work)}; {passed} passed so far.", flush=True)
        if fatal_provider_error:
            print("Stopping after a non-transient provider error.", flush=True)
            break

    baseline = [item for item in observations if item["repeat_no"] == 1]
    repeated = [item for item in observations if item["repeat_no"] > 1]
    supported = [item for item in baseline if item["expected_outcome"] == "CALL"]
    no_tool = [item for item in baseline if item["expected_outcome"] == "NO_BUSINESS_TOOL"]
    all_no_tool = [item for item in observations if item["expected_outcome"] == "NO_BUSINESS_TOOL"]
    by_intent: dict[str, dict[str, int]] = {}
    for item in supported:
        key = item["expected_intent"] or "UNKNOWN"
        agg = by_intent.setdefault(
            key, {"total": 0, "passed": 0, "first_action_passed": 0, "schema_valid": 0}
        )
        agg["total"] += 1
        agg["passed"] += int(item["pass"])
        call = item.get("normalized_call") or {}
        args = call.get("arguments") or {}
        expected = next(
            case["expected"]
            for case in EVALUATION_CASES
            if case["case_id"] == item["case_id"]
        )
        agg["first_action_passed"] += int(args.get("action") in expected["allowed_actions"])
        agg["schema_valid"] += int(item.get("schema_valid") is True)

    critical_observations = [item for item in all_no_tool if "critical_no_tool" in item["tags"]]
    errors = [item for item in observations if "provider_error" in item["failure_reasons"]]
    parsed_tool_attempts = [item for item in observations if item["tool_call_count"] > 0]
    prompt_for_hash = "\n".join(
        (*AGENT_INSTRUCTIONS, CUSTOMER_TASK_TOOL_INSTRUCTIONS, "Use markdown to format answers.")
    )
    endpoint_host = urlparse(settings.model_base_url).hostname or "configured-endpoint"
    rubric_path = ROOT / "rubric-v4.md"
    expectation_bytes = json.dumps(
        EXPECTATION_OVERRIDES, ensure_ascii=False, sort_keys=True
    ).encode("utf-8")
    report = {
        "suite_version": SUITE_VERSION,
        "rubric_version": RUBRIC_VERSION,
        "runner_version": RUNNER_VERSION,
        "code_revision": _git_revision(),
        "model_id": settings.model_id,
        "endpoint_alias": endpoint_host,
        "generation_parameters": {
            "temperature": 0,
            "max_tokens": 500,
            "tool_choice": "auto",
            "timeout_seconds": 90,
        },
        "prompt_sha256": _sha256(prompt_for_hash.encode("utf-8")),
        "tool_schema_sha256": _sha256(
            json.dumps(tool_schemas, sort_keys=True, ensure_ascii=False).encode("utf-8")
        ),
        "case_suite_sha256": _sha256(
            json.dumps(CASES_V1, ensure_ascii=False, sort_keys=True).encode("utf-8")
        ),
        "expectation_overlay_sha256": _sha256(expectation_bytes),
        "rubric_sha256": _sha256(rubric_path.read_bytes()),
        "runner_sha256": _sha256(Path(__file__).read_bytes()),
        "started_at_utc": observations[0]["observed_at_utc"] if observations else None,
        "completed_at_utc": datetime.now(timezone.utc).isoformat(),
        "case_count": len(EVALUATION_CASES),
        "evaluation_case_ids": [case["case_id"] for case in EVALUATION_CASES],
        "observation_count": len(observations),
        "repeat_policy": (
            "baseline once; critical_no_tool and high_confusion cases "
            "repeated three additional times"
        ),
        "metrics": {
            "supported_routes": {
                "passed": sum(item["pass"] for item in supported),
                "total": len(supported),
            },
            "supported_by_intent": by_intent,
            "expected_no_tool": {
                "passed": sum(item["pass"] for item in no_tool),
                "total": len(no_tool),
            },
            "expected_clarification": {
                "passed": sum(
                    item["pass"]
                    for item in baseline
                    if item["expected_outcome"] == "CALL_OR_CLARIFY"
                ),
                "total": sum(
                    item["expected_outcome"] == "CALL_OR_CLARIFY" for item in baseline
                ),
            },
            "critical_no_tool_business_calls": sum(
                item["tool_call_count"] > 0 for item in critical_observations
            ),
            "argument_schema_validity": {
                "valid": sum(item.get("schema_valid") is True for item in parsed_tool_attempts),
                "attempted": len(parsed_tool_attempts),
            },
            "schema_invalid_calls": sum(
                item.get("schema_valid") is False for item in parsed_tool_attempts
            ),
            "argument_violation_observations": sum(
                bool(item["argument_violations"]) for item in observations
            ),
            "manual_review_cases": len(
                {item["case_id"] for item in observations if item["manual_review"]}
            ),
            "repeat_observations": len(repeated),
            "repeat_passed": sum(item["pass"] for item in repeated),
            "repeat_critical_no_tool_business_calls": sum(
                item["tool_call_count"] > 0
                for item in repeated
                if item["expected_outcome"] == "NO_BUSINESS_TOOL"
                and "critical_no_tool" in item["tags"]
            ),
            "provider_errors": len(errors),
            "mean_latency_ms": round(
                sum(item["latency_ms"] for item in observations) / max(1, len(observations)), 1
            ),
            "p95_latency_ms": sorted(item["latency_ms"] for item in observations)[
                max(0, math.ceil(len(observations) * 0.95) - 1)
            ]
            if observations
            else None,
        },
        "observations": observations,
    }
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report["metrics"], ensure_ascii=False, indent=2))
    print(f"Report written to {REPORT_PATH}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())

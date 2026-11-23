"""Re-score narrow-tool first-call observations against the frozen audited rubric v3."""

from __future__ import annotations

import hashlib
import json
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from case_audit_v2 import CASE_MODES, FOLLOW_UP_REQUIRED

from app.schemas.customer_task import CustomerTaskRequest

HERE = Path(__file__).resolve().parent
INPUT = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "runs" / "post-fix-v1.json"
OUTPUT = Path(sys.argv[2]) if len(sys.argv) > 2 else HERE / "runs" / "post-fix-v3.json"
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


def _tool_call(item: dict[str, Any]) -> tuple[str | None, dict[str, Any] | None]:
    calls = item.get("raw_tool_calls") or []
    if len(calls) != 1:
        return None, None
    call = calls[0]
    function = call.get("function") or {}
    name = function.get("name")
    arguments = function.get("arguments")
    if isinstance(arguments, str):
        try:
            arguments = json.loads(arguments)
        except json.JSONDecodeError:
            return name, None
    return name, arguments if isinstance(arguments, dict) else None


def _normalized_request(name: str, arguments: dict[str, Any]) -> dict[str, Any] | None:
    binding = TOOL_BINDINGS.get(name)
    if binding is None:
        return None
    intent, action = binding
    normalized = {"intent": intent, "action": action, **arguments}
    if action in {"SEARCH_PRODUCTS", "RECOMMEND_PRODUCTS"}:
        normalized["limit"] = 3
    if action == "SEARCH_PRODUCTS":
        for optional in ("category_id", "keyword"):
            if normalized.get(optional) == "":
                normalized.pop(optional)
    return normalized


def _valid_call(
    item: dict[str, Any],
) -> tuple[str | None, str | None, dict[str, Any] | None, str | None]:
    name, arguments = _tool_call(item)
    if name is None or arguments is None:
        return None, None, None, "expected_exactly_one_parseable_tool_call"
    normalized = _normalized_request(name, arguments)
    if normalized is None:
        return None, None, None, "unknown_tool_name"
    intent, action = TOOL_BINDINGS[name]
    try:
        CustomerTaskRequest.model_validate(normalized)
    except Exception as exc:
        return intent, action, normalized, f"schema_invalid:{type(exc).__name__}"
    return intent, action, normalized, None


def _score(item: dict[str, Any], case: dict[str, Any]) -> dict[str, Any]:
    case_id = item["case_id"]
    expected = case["expected"]
    mode = CASE_MODES.get(case_id, {}).get("mode")
    text = item.get("final_text", "").strip()
    calls = item.get("raw_tool_calls") or []
    result = {"case_id": case_id, "repeat_no": item["repeat_no"], "status": "FAIL", "reason": ""}

    if case_id in FOLLOW_UP_REQUIRED:
        intent, action, normalized, error = _valid_call(item)
        if error is None and (intent, action) == ("PRODUCT_CATALOG", "LIST_CATEGORIES"):
            result.update(
                status="FOLLOW_UP_REQUIRED",
                reason=(
                    "Safe category prerequisite; the first-call run cannot verify that "
                    "product search follows."
                ),
                normalized_call={"intent": intent, "action": action, "arguments": normalized},
            )
            return result

    if mode == "NO_TOOL_CLARIFICATION":
        passed = not calls and bool(text)
        result.update(
            status="PASS" if passed else "FAIL",
            reason="Clarification without a business call."
            if passed
            else "Expected a non-empty clarification and no business call.",
        )
        return result

    if mode in {"SUPPORTED_FIRST_STEP_OR_CLARIFY", "CALL_OR_CLARIFY"} and not calls and text:
        result.update(
            status="PASS_MANUAL_REVIEW",
            reason=(
                "Clarification is accepted by the audited case; review whether it asks "
                "for the needed detail."
            ),
        )
        return result

    if expected["outcome"] == "NO_BUSINESS_TOOL" and mode not in {
        "SUPPORTED_FIRST_STEP_OR_CLARIFY",
        "CALL_OR_CLARIFY",
        "CALL_PAIRS",
    }:
        passed = not calls and bool(text)
        result.update(
            status="PASS" if passed else "FAIL",
            reason="No business tool and non-empty answer."
            if passed
            else "Unexpected business call or empty answer.",
        )
        return result

    intent, action, normalized, error = _valid_call(item)
    if error:
        result["reason"] = error
        return result

    if mode in {"CALL_PAIRS", "SUPPORTED_FIRST_STEP_OR_CLARIFY", "CALL_OR_CLARIFY"}:
        accepted_pairs = {tuple(pair) for pair in CASE_MODES[case_id].get("allowed_pairs", [])}
    else:
        accepted_pairs = {
            (allowed_intent, allowed_action)
            for allowed_intent in expected["allowed_intents"]
            for allowed_action in expected["allowed_actions"]
        }
    if (intent, action) not in accepted_pairs:
        result["reason"] = "tool_intent_action_not_allowed"
        return result

    missing = [
        field for field in expected["required_args"] if normalized.get(field) in (None, "", [])
    ]
    missing_groups = [
        group
        for group in expected["required_one_of"]
        if not any(normalized.get(field) not in (None, "", []) for field in group)
    ]
    forbidden = [field for field in expected["forbidden_args"] if normalized.get(field) is not None]
    if missing or missing_groups or forbidden:
        result["reason"] = "argument_contract_violation"
        result["argument_violations"] = {
            "missing": missing,
            "missing_one_of": missing_groups,
            "forbidden": forbidden,
        }
        return result

    result.update(
        status="PASS_MANUAL_REVIEW"
        if mode in {"CALL_PAIRS", "SUPPORTED_FIRST_STEP_OR_CLARIFY", "CALL_OR_CLARIFY"}
        else "PASS",
        reason="Accepted first-step route; review subsequent answer separately."
        if mode
        else "Matches frozen case route.",
        normalized_call={
            "name": _tool_call(item)[0],
            "intent": intent,
            "action": action,
            "arguments": normalized,
        },
    )
    return result


def main() -> int:
    source = json.loads(INPUT.read_text(encoding="utf-8"))
    from cases_v1 import CASES

    case_by_id = {case["case_id"]: case for case in CASES}
    scored = [_score(item, case_by_id[item["case_id"]]) for item in source["observations"]]
    counts = Counter(item["status"] for item in scored)
    baseline = [item for item in scored if item["repeat_no"] == 1]
    repeats = [item for item in scored if item["repeat_no"] > 1]
    baseline_counts = Counter(item["status"] for item in baseline)
    repeat_counts = Counter(item["status"] for item in repeats)
    rubric_bytes = (HERE / "rubric-v3.md").read_bytes()
    suite_bytes = json.dumps(CASES, ensure_ascii=False, sort_keys=True).encode("utf-8")
    report = {
        "rubric_version": "intent-routing-rubric-v3",
        "rubric_sha256": hashlib.sha256(rubric_bytes).hexdigest(),
        "case_suite_sha256": hashlib.sha256(suite_bytes).hexdigest(),
        "source_run": str(INPUT),
        "source_run_started_at_utc": source.get("started_at_utc"),
        "source_run_completed_at_utc": source.get("completed_at_utc"),
        "model_id": source.get("model_id"),
        "endpoint_alias": source.get("endpoint_alias"),
        "new_llm_calls": 0,
        "observations": scored,
        "summary": {
            "all_observations": dict(counts),
            "base_observations": dict(baseline_counts),
            "repeat_observations": dict(repeat_counts),
            "remaining_failures": [item for item in scored if item["status"] == "FAIL"],
            "follow_up_required": [
                item for item in scored if item["status"] == "FOLLOW_UP_REQUIRED"
            ],
        },
        "scoring_note": (
            "Optional empty category_id/keyword values are treated as omitted in line with "
            "the adapter fix, verified by test_customer_task_dispatcher."
        ),
        "rescored_at_utc": datetime.now(timezone.utc).isoformat(),
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report["summary"], ensure_ascii=False, indent=2))
    print("Report written to", OUTPUT)
    return 1 if report["summary"]["remaining_failures"] else 0


if __name__ == "__main__":
    raise SystemExit(main())

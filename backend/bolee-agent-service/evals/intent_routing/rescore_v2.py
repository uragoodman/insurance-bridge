"""Re-score saved v1 raw observations with the audited v2 case expectations."""

from __future__ import annotations

import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any

from case_audit_v2 import CASE_MODES, FOLLOW_UP_REQUIRED

from app.schemas.customer_task import CustomerTaskRequest

HERE = Path(__file__).resolve().parent
INPUT = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "runs" / "baseline-v1.json"
OUTPUT = Path(sys.argv[2]) if len(sys.argv) > 2 else HERE / "runs" / "rescore-v2.json"


def _call_details(item: dict[str, Any]) -> tuple[str | None, dict[str, Any] | None]:
    calls = item.get("raw_tool_calls") or []
    if len(calls) != 1:
        return None, None
    call = calls[0]
    function = call.get("function") or {}
    name = function.get("name")
    args = function.get("arguments")
    if isinstance(args, str):
        try:
            args = json.loads(args)
        except json.JSONDecodeError:
            return name, None
    return name, args if isinstance(args, dict) else None


def _valid_schema(args: dict[str, Any] | None) -> bool:
    if args is None:
        return False
    try:
        CustomerTaskRequest.model_validate(args)
        return True
    except Exception:
        return False


def _score(item: dict[str, Any]) -> tuple[str, str]:
    case_id = item["case_id"]
    final_text = item.get("final_text", "").strip()
    calls = item.get("raw_tool_calls") or []
    mode = CASE_MODES.get(case_id, {}).get("mode")
    if case_id in FOLLOW_UP_REQUIRED:
        name, args = _call_details(item)
        pair = (args or {}).get("intent"), (args or {}).get("action")
        if (
            name == "handle_customer_task"
            and pair == ("PRODUCT_CATALOG", "LIST_CATEGORIES")
            and _valid_schema(args)
        ):
            return "FOLLOW_UP_REQUIRED", FOLLOW_UP_REQUIRED[case_id]

    if mode == "NO_TOOL_CLARIFICATION":
        passed = not calls and bool(final_text)
        return (
            ("PASS", "No-tool clarification matches audited expectation.")
            if passed
            else ("FAIL", "Expected a non-empty clarification without a business-tool call.")
        )

    if mode in {"SUPPORTED_FIRST_STEP_OR_CLARIFY", "CALL_OR_CLARIFY"}:
        if not calls and final_text:
            return "PASS_MANUAL_REVIEW", "Clarification path accepted; inspect text manually."

    if mode in {"CALL_PAIRS", "SUPPORTED_FIRST_STEP_OR_CLARIFY", "CALL_OR_CLARIFY"}:
        name, args = _call_details(item)
        pair = ((args or {}).get("intent"), (args or {}).get("action"))
        allowed_pairs = CASE_MODES[case_id]["allowed_pairs"]
        if name == "handle_customer_task" and pair in allowed_pairs and _valid_schema(args):
            return "PASS_MANUAL_REVIEW", "Allowed first action; inspect final response manually."
        return (
            "FAIL",
            "No accepted route/clarification, or tool arguments failed contract validation.",
        )

    return (
        ("PASS", "Matches frozen v1 label.")
        if item.get("pass")
        else ("FAIL", ";".join(item.get("failure_reasons", [])) or "Failed frozen v1 label.")
    )


def main() -> int:
    report = json.loads(INPUT.read_text(encoding="utf-8"))
    rescored = []
    for item in report["observations"]:
        status, reason = _score(item)
        rescored.append(
            {
                "case_id": item["case_id"],
                "repeat_no": item["repeat_no"],
                "status": status,
                "reason": reason,
                "original_v1_pass": item["pass"],
                "failure_reasons_v1": item.get("failure_reasons", []),
            }
        )

    counts = Counter(item["status"] for item in rescored)
    baseline = [item for item in rescored if item["repeat_no"] == 1]
    repeats = [item for item in rescored if item["repeat_no"] > 1]
    baseline_counts = Counter(item["status"] for item in baseline)
    repeat_counts = Counter(item["status"] for item in repeats)
    failed = [item for item in rescored if item["status"] == "FAIL"]
    pending = [item for item in rescored if item["status"] == "FOLLOW_UP_REQUIRED"]

    output = {
        "rubric_version": "intent-routing-rubric-v2-case-audit",
        "source_report": str(INPUT),
        "model_observations_reused": len(rescored),
        "new_llm_calls": 0,
        "all_observations": dict(counts),
        "baseline_once": dict(baseline_counts),
        "repeat_observations": dict(repeat_counts),
        "baseline_scored_pass_rate": {
            "passed": baseline_counts["PASS"] + baseline_counts["PASS_MANUAL_REVIEW"],
            "scored": len(baseline) - baseline_counts["FOLLOW_UP_REQUIRED"],
        },
        "repeat_scored_pass_rate": {
            "passed": repeat_counts["PASS"] + repeat_counts["PASS_MANUAL_REVIEW"],
            "scored": len(repeats) - repeat_counts["FOLLOW_UP_REQUIRED"],
        },
        "follow_up_case_observations": pending,
        "remaining_failures": failed,
        "observations": rescored,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(
        json.dumps(
            {
                key: value
                for key, value in output.items()
                if key not in {"observations", "remaining_failures", "follow_up_case_observations"}
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    print("Remaining failed observations:")
    for item in failed:
        print(item["case_id"], "repeat", item["repeat_no"], item["reason"])
    print("Follow-up required:")
    for item in pending:
        print(item["case_id"], "repeat", item["repeat_no"])
    print("Report written to", OUTPUT)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

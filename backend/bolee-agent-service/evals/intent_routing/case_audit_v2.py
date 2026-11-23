"""Case expectation corrections for rescore v1 observations under rubric v2."""

from __future__ import annotations

from typing import Any

CASE_MODES: dict[str, dict[str, Any]] = {
    "terms-07": {
        "mode": "NO_TOOL_CLARIFICATION",
        "rationale": "No product was named; asking which product/version is valid.",
    },
    "ambiguous-03": {
        "mode": "SUPPORTED_FIRST_STEP_OR_CLARIFY",
        "allowed_pairs": [("PRODUCT_RECOMMENDATION", "LIST_CATEGORIES")],
        "rationale": (
            "Proceed with the explicitly first supported goal or clarify; "
            "cancellation stays unsupported."
        ),
    },
    "ambiguous-09": {
        "mode": "SUPPORTED_FIRST_STEP_OR_CLARIFY",
        "allowed_pairs": [
            ("PRODUCT_RECOMMENDATION", "LIST_CATEGORIES"),
            ("PRODUCT_CATALOG", "LIST_CATEGORIES"),
        ],
        "rationale": (
            "Recommendation may be handled first; terms lookup can wait "
            "until a product is identified."
        ),
    },
    "recommend-01": {
        "mode": "CALL_PAIRS",
        "allowed_pairs": [
            ("PRODUCT_CATALOG", "LIST_CATEGORIES"),
            ("PRODUCT_RECOMMENDATION", "LIST_CATEGORIES"),
        ],
        "rationale": "The user asks to see categories first; both intents permit this action.",
    },
    "catalog-02": {
        "mode": "CALL_PAIRS",
        "allowed_pairs": [
            ("PRODUCT_CATALOG", "SEARCH_PRODUCTS"),
            ("PRODUCT_RECOMMENDATION", "LIST_CATEGORIES"),
        ],
        "rationale": (
            "Suitable products can mean catalog browsing or the first step of recommendation."
        ),
    },
    "catalog-06": {
        "mode": "CALL_PAIRS",
        "allowed_pairs": [
            ("PRODUCT_CATALOG", "SEARCH_PRODUCTS"),
            ("PRODUCT_RECOMMENDATION", "LIST_CATEGORIES"),
        ],
        "rationale": (
            "‘Suitable for self-driving’ can start a keyword search or a recommendation workflow."
        ),
    },
    "terms-03": {
        "mode": "CALL_OR_CLARIFY",
        "allowed_pairs": [("TERMS_QA", "SEARCH_PRODUCT_TERMS")],
        "rationale": (
            "The user supplied a product type but not a unique product; "
            "a valid lookup or clarification is acceptable."
        ),
    },
    "terms-04": {
        "mode": "CALL_OR_CLARIFY",
        "allowed_pairs": [
            ("TERMS_QA", "SEARCH_PRODUCT_TERMS"),
            ("PRODUCT_CATALOG", "SEARCH_PRODUCTS"),
        ],
        "rationale": (
            "The product is unspecified; search candidates first, query "
            "terms, or ask for the product."
        ),
    },
    "terms-08": {
        "mode": "CALL_OR_CLARIFY",
        "allowed_pairs": [("TERMS_QA", "SEARCH_PRODUCT_TERMS")],
        "rationale": (
            "The user supplied a product type but not a unique product; "
            "a valid lookup or clarification is acceptable."
        ),
    },
}

FOLLOW_UP_REQUIRED: dict[str, str] = {
    "catalog-01": (
        "LIST_CATEGORIES may be a safe preliminary step; this one-call "
        "baseline cannot show whether product search follows."
    ),
    "catalog-05": (
        "LIST_CATEGORIES may be a safe preliminary step; this one-call "
        "baseline cannot show whether product search follows."
    ),
    "catalog-07": (
        "LIST_CATEGORIES may be a safe preliminary step; this one-call "
        "baseline cannot show whether product search follows."
    ),
}

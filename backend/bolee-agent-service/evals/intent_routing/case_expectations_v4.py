"""Scoring-only clarifications for frozen intent-routing cases v1."""

from __future__ import annotations

EXPECTATION_OVERRIDES: dict[str, dict[str, object]] = {
    # These utterances can reasonably start either product browsing or recommendation setup.
    "catalog-02": {
        "allowed_pairs": [
            ["PRODUCT_CATALOG", "SEARCH_PRODUCTS"],
            ["PRODUCT_RECOMMENDATION", "LIST_CATEGORIES"],
        ],
    },
    "recommend-01": {
        "allowed_pairs": [
            ["PRODUCT_CATALOG", "LIST_CATEGORIES"],
            ["PRODUCT_RECOMMENDATION", "LIST_CATEGORIES"],
            ["PRODUCT_RECOMMENDATION", "GET_RECOMMENDATION_CRITERIA"],
        ],
    },
    "recommend-05": {
        "allowed_pairs": [
            ["PRODUCT_CATALOG", "LIST_CATEGORIES"],
            ["PRODUCT_RECOMMENDATION", "LIST_CATEGORIES"],
            ["PRODUCT_RECOMMENDATION", "GET_RECOMMENDATION_CRITERIA"],
        ],
    },
    # When a product is not uniquely named, either ask for it or let Java resolve a candidate.
    "terms-03": {
        "outcome": "CALL_OR_CLARIFY",
    },
    "terms-07": {
        "outcome": "CALL_OR_CLARIFY",
    },
    "terms-08": {
        "outcome": "CALL_OR_CLARIFY",
    },
}

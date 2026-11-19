"""Validated application use cases for Java-backed product recommendations."""

import logging
import re
from typing import Any

from pydantic import ValidationError

from app.clients.java_internal_api import JavaApiError
from app.contexts.tool import AgentToolContext
from app.schemas import JavaResponseEnvelope
from app.schemas.product_recommendation import (
    CriteriaCatalogStatus,
    CustomerIntent,
    ProductRecommendationCriteriaCatalog,
    ProductRecommendationRequest,
    ProductRecommendationResponse,
    ProductRecommendationStatus,
    RecommendationRuleMode,
    RecommendationValueType,
)

logger = logging.getLogger("bolee-agent.product-recommendation")


class ProductRecommendationServiceError(RuntimeError):
    """Sanitized recommendation failure safe to expose to the agent."""


class ProductRecommendationService:
    _CRITERIA_PATH = "/internal/agent-tools/v1/products/recommendation-criteria"
    _RECOMMENDATIONS_PATH = "/internal/agent-tools/v1/products/recommendations"
    _JAVA_SUCCESS_CODE = 200
    _CATEGORY_ID_PATTERN = re.compile(r"^[0-9]{1,19}$")

    def get_criteria_catalog(
        self,
        category_id: str,
        context: AgentToolContext,
    ) -> ProductRecommendationCriteriaCatalog:
        if not self._CATEGORY_ID_PATTERN.fullmatch(category_id or ""):
            raise ProductRecommendationServiceError("A valid Java product category ID is required.")

        try:
            raw = context.java_internal_api.get_json(
                self._CRITERIA_PATH,
                authorization_context=context.authorization_context,
                params={"categoryId": category_id},
            )
            envelope = JavaResponseEnvelope[ProductRecommendationCriteriaCatalog].model_validate(
                raw
            )
        except JavaApiError as exc:
            logger.warning("Recommendation criteria query failed")
            raise ProductRecommendationServiceError(
                "Recommendation criteria are temporarily unavailable."
            ) from exc
        except ValidationError as exc:
            logger.warning("Invalid Java recommendation criteria contract")
            raise ProductRecommendationServiceError(
                "Recommendation criteria returned an invalid response."
            ) from exc

        if envelope.code != self._JAVA_SUCCESS_CODE or envelope.data is None:
            logger.warning("Java recommendation criteria envelope rejected; code=%s", envelope.code)
            raise ProductRecommendationServiceError(
                "Recommendation criteria are temporarily unavailable."
            )
        if envelope.data.category_id != category_id:
            raise ProductRecommendationServiceError("Java returned a category mismatch.")
        return envelope.data

    def recommend(
        self,
        request: ProductRecommendationRequest | dict[str, Any],
        context: AgentToolContext,
    ) -> ProductRecommendationResponse:
        context.result_collector.clear()
        try:
            recommendation_request = ProductRecommendationRequest.model_validate(request)
        except ValidationError as exc:
            raise ProductRecommendationServiceError(
                "Product recommendation request is invalid."
            ) from exc

        catalog = self.get_criteria_catalog(recommendation_request.criteria.category_id, context)
        if catalog.status != CriteriaCatalogStatus.READY:
            result = self._local_response(
                recommendation_request,
                ProductRecommendationStatus.CATALOG_NOT_READY,
                catalog,
            )
            context.result_collector.record_product_recommendation(result)
            return result

        unsupported = self._unsupported_criteria(recommendation_request, catalog)
        if unsupported:
            result = self._local_response(
                recommendation_request,
                ProductRecommendationStatus.UNSUPPORTED_CRITERIA,
                catalog,
                unsupported_criteria=unsupported,
            )
            context.result_collector.record_product_recommendation(result)
            return result
        self._validate_criterion_values(recommendation_request, catalog)

        try:
            raw = context.java_internal_api.post_json(
                self._RECOMMENDATIONS_PATH,
                authorization_context=context.authorization_context,
                json_body=recommendation_request.model_dump(
                    mode="python", by_alias=True, exclude_none=True
                ),
            )
            envelope = JavaResponseEnvelope[ProductRecommendationResponse].model_validate(raw)
        except JavaApiError as exc:
            logger.warning("Product recommendation request failed")
            raise ProductRecommendationServiceError(
                "Product recommendation is temporarily unavailable."
            ) from exc
        except ValidationError as exc:
            logger.warning("Invalid Java recommendation response contract")
            raise ProductRecommendationServiceError(
                "Product recommendation returned an invalid response."
            ) from exc

        if envelope.code != self._JAVA_SUCCESS_CODE or envelope.data is None:
            logger.warning("Java recommendation envelope rejected; code=%s", envelope.code)
            raise ProductRecommendationServiceError(
                "Product recommendation is temporarily unavailable."
            )

        result = envelope.data
        if result.request_id != recommendation_request.request_id:
            raise ProductRecommendationServiceError("Java returned a request ID mismatch.")
        if result.status != ProductRecommendationStatus.CATALOG_NOT_READY:
            if (
                result.catalog_version != catalog.catalog_version
                or result.rule_version != catalog.rule_version
            ):
                raise ProductRecommendationServiceError(
                    "Recommendation catalog changed during evaluation. "
                    "Refresh the criteria and retry."
                )
        if any(candidate.category_id != recommendation_request.criteria.category_id
               for candidate in result.candidates):
            raise ProductRecommendationServiceError("Java returned a category mismatch.")
        if any(
            candidate.verification_status != "VERIFIED_MATCH" for candidate in result.candidates
        ):
            raise ProductRecommendationServiceError(
                "Java returned an unverified product candidate."
            )
        if len(result.candidates) > recommendation_request.limit:
            raise ProductRecommendationServiceError("Java returned too many product candidates.")
        compare_definitions = {
            criterion.code: criterion
            for criterion in catalog.criteria
            if RecommendationRuleMode.COMPARE_ONLY in criterion.allowed_uses
        }
        for candidate in result.candidates:
            candidate_facts = {fact.code: fact for fact in candidate.facts}
            if set(candidate_facts) != set(compare_definitions):
                raise ProductRecommendationServiceError(
                    "Java returned an incomplete comparison fact set."
                )
            if any(
                candidate_facts[code].definition_version != definition.definition_version
                for code, definition in compare_definitions.items()
            ):
                raise ProductRecommendationServiceError(
                    "Java returned comparison facts for a different definition version."
                )
        context.result_collector.record_product_recommendation(result)
        return result

    def _unsupported_criteria(
        self,
        request: ProductRecommendationRequest,
        catalog: ProductRecommendationCriteriaCatalog,
    ) -> list[str]:
        definitions = {criterion.code: criterion for criterion in catalog.criteria}
        unsupported: list[str] = []
        for item in request.criteria.items:
            definition = definitions.get(item.code)
            if definition is None:
                unsupported.append(item.code)
                continue
            expected_mode = (
                RecommendationRuleMode.HARD_FILTER
                if item.customer_intent == CustomerIntent.MUST_HAVE
                else RecommendationRuleMode.PREFERENCE_SCORE
            )
            if (
                definition.active_rule_mode != expected_mode
                or expected_mode not in definition.allowed_uses
                or item.operator not in definition.allowed_operators
            ):
                unsupported.append(item.code)
        return unsupported

    def _validate_criterion_values(
        self,
        request: ProductRecommendationRequest,
        catalog: ProductRecommendationCriteriaCatalog,
    ) -> None:
        definitions = {criterion.code: criterion for criterion in catalog.criteria}
        for item in request.criteria.items:
            definition = definitions[item.code]
            value_type = definition.value_type
            operator = item.operator
            options = {option.value_code for option in definition.options}

            if operator == "EQ":
                if value_type == RecommendationValueType.ENUM:
                    if (not item.value_code or item.value_code not in options
                            or self._has_numeric_values(item) or item.value_codes is not None):
                        raise ProductRecommendationServiceError(
                            f"Criterion '{item.code}' has an invalid enum value."
                        )
                elif value_type == RecommendationValueType.NUMBER:
                    if (item.number_value is None or self._has_symbolic_values(item)
                            or item.min_value is not None or item.max_value is not None):
                        raise ProductRecommendationServiceError(
                            f"Criterion '{item.code}' requires one numeric value."
                        )
                else:
                    raise ProductRecommendationServiceError(
                        f"EQ is not valid for criterion '{item.code}'."
                    )
            elif operator == "IN":
                invalid_in_values = (
                    value_type not in {
                        RecommendationValueType.ENUM,
                        RecommendationValueType.MULTI_ENUM,
                    }
                    or not item.value_codes
                    or any(value not in options for value in item.value_codes)
                    or item.value_code is not None
                    or self._has_numeric_values(item)
                )
                if invalid_in_values:
                    raise ProductRecommendationServiceError(
                        f"Criterion '{item.code}' has invalid IN values."
                    )
            elif operator in {"LTE", "GTE"}:
                if (value_type != RecommendationValueType.NUMBER
                        or item.number_value is None
                        or self._has_symbolic_values(item)
                        or item.min_value is not None
                        or item.max_value is not None):
                    raise ProductRecommendationServiceError(
                        f"Criterion '{item.code}' requires one numeric value."
                    )
            elif operator == "RANGE":
                invalid_range_values = (
                    value_type not in {
                        RecommendationValueType.NUMBER,
                        RecommendationValueType.NUMBER_RANGE,
                    }
                    or item.min_value is None
                    or item.max_value is None
                    or item.min_value > item.max_value
                    or item.value_code is not None
                    or item.value_codes is not None
                    or item.number_value is not None
                )
                if invalid_range_values:
                    raise ProductRecommendationServiceError(
                        f"Criterion '{item.code}' has an invalid numeric range."
                    )
            else:
                raise ProductRecommendationServiceError(
                    f"Criterion '{item.code}' uses an unsupported operator."
                )

            numeric = value_type in {
                RecommendationValueType.NUMBER,
                RecommendationValueType.NUMBER_RANGE,
            }
            if numeric:
                if not definition.unit or item.unit != definition.unit:
                    raise ProductRecommendationServiceError(
                        f"Criterion '{item.code}' must use unit '{definition.unit}'."
                    )
            elif item.unit:
                raise ProductRecommendationServiceError(
                    f"Criterion '{item.code}' does not accept a unit."
                )

    @staticmethod
    def _has_numeric_values(item: Any) -> bool:
        return any(
            value is not None for value in (item.number_value, item.min_value, item.max_value)
        )

    @staticmethod
    def _has_symbolic_values(item: Any) -> bool:
        return item.value_code is not None or item.value_codes is not None

    @staticmethod
    def _local_response(
        request: ProductRecommendationRequest,
        status: ProductRecommendationStatus,
        catalog: ProductRecommendationCriteriaCatalog,
        unsupported_criteria: list[str] | None = None,
    ) -> ProductRecommendationResponse:
        return ProductRecommendationResponse(
            request_id=request.request_id,
            status=status,
            evaluation_completeness="COMPLETE",
            missing_criteria=[],
            unsupported_criteria=unsupported_criteria or [],
            unresolved_criteria=[],
            excluded_product_count=0,
            candidates=[],
            catalog_version=catalog.catalog_version,
            rule_version=catalog.rule_version,
        )

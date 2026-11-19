"""Validated HTTP use case for comparing published insurance products in Java."""

import logging
from typing import Any

from pydantic import ValidationError

from app.clients.java_internal_api import JavaApiError
from app.contexts.tool import AgentToolContext
from app.schemas import JavaResponseEnvelope
from app.schemas.product_comparison import (
    ProductComparisonRequest,
    ProductComparisonResponse,
    ProductComparisonStatus,
)

logger = logging.getLogger("bolee-agent.product-comparison")


class ProductComparisonServiceError(RuntimeError):
    """Sanitized comparison failure safe to expose to the agent."""


class ProductComparisonService:
    _COMPARE_PATH = "/internal/agent-tools/v1/products/compare"
    _JAVA_SUCCESS_CODE = 200

    def compare(
        self,
        request: ProductComparisonRequest | dict[str, Any],
        context: AgentToolContext,
    ) -> ProductComparisonResponse:
        context.result_collector.clear()
        try:
            comparison_request = ProductComparisonRequest.model_validate(request)
        except ValidationError as exc:
            raise ProductComparisonServiceError("Product comparison request is invalid.") from exc

        try:
            raw = context.java_internal_api.post_json(
                self._COMPARE_PATH,
                authorization_context=context.authorization_context,
                json_body=comparison_request.model_dump(
                    mode="python", by_alias=True, exclude_none=True
                ),
            )
            envelope = JavaResponseEnvelope[ProductComparisonResponse].model_validate(raw)
        except JavaApiError as exc:
            logger.warning("Product comparison request failed")
            raise ProductComparisonServiceError(
                "Product comparison is temporarily unavailable."
            ) from exc
        except ValidationError as exc:
            logger.warning("Invalid Java product comparison response contract")
            raise ProductComparisonServiceError(
                "Product comparison returned an invalid response."
            ) from exc

        if envelope.code != self._JAVA_SUCCESS_CODE or envelope.data is None:
            logger.warning("Java comparison envelope rejected; code=%s", envelope.code)
            raise ProductComparisonServiceError("Product comparison is temporarily unavailable.")

        result = envelope.data
        if result.request_id != comparison_request.request_id:
            raise ProductComparisonServiceError("Java returned a request ID mismatch.")
        if result.category_id != comparison_request.category_id:
            raise ProductComparisonServiceError("Java returned a category mismatch.")
        if result.status != ProductComparisonStatus.CATALOG_NOT_READY:
            requested_ids = [str(int(product_id)) for product_id in comparison_request.product_ids]
            returned_ids = [product.product_id for product in result.products]
            if returned_ids != requested_ids:
                raise ProductComparisonServiceError(
                    "Java returned a different product set or order."
                )
        context.result_collector.record_product_comparison(result)
        return result

"""Narrow LLM-facing tools backed by the centralized customer-task dispatcher."""

from typing import Annotated, Any, Literal

from agno.run import RunContext
from agno.tools import Toolkit
from pydantic import Field, ValidationError

from app.domain.customer_task import CustomerTaskIntent
from app.schemas.customer_task import CustomerTaskAction, CustomerTaskRequest
from app.schemas.product_recommendation import ProductRecommendationCriteria
from app.services.customer_task_dispatcher import CustomerTaskDispatcher

CustomerTaskKeyword = Annotated[str, Field(min_length=1, max_length=100)]
ProductCategoryId = Annotated[str, Field(min_length=1, max_length=19, pattern=r"^[0-9]{1,19}$")]
OptionalProductCategoryId = Annotated[
    str | None,
    Field(
        description=(
            "Optional Java category ID. Omit this field when no Java category ID was returned."
        )
    ),
]
PolicyId = Annotated[str, Field(min_length=1, max_length=19, pattern=r"^[0-9]{1,19}$")]
ProviderNullSentinel = Annotated[
    Literal["null"] | None,
    Field(description="Optional provider compatibility sentinel; omit or use the literal null."),
]

CUSTOMER_TASK_TOOL_INSTRUCTIONS = (
    "Route by the customer's primary intent, using exactly one matching narrow tool for the "
    "first step. If a message requests two independent goals (for example recommend a product "
    "and cancel an old policy, or recommend and also search terms), ask which goal to handle "
    "first and call no business tool, even if the user wrote an order. Do not start either goal "
    "until the user chooses. For a direct request to find/list/search products, call "
    "search_catalog_products with the user's product and scenario terms; do not list categories "
    "first. Use list_catalog_categories only when the user explicitly wants to browse the "
    "catalog's category list. Use list_recommendation_categories only when the primary goal is "
    "personalized product recommendation and category selection is needed. If the user asks to "
        "compare, contrast, or find products and compare them, always start with "
        "search_comparison_products, never a recommendation-category tool. Put all named products "
        "into one keyword string and make exactly one tool call in the assistant response; never "
        "call once per product. Then compare only IDs "
    "returned by Java in this run. For recommendation, read published criteria before "
    "recommend_products and use only returned criterion codes and values. For formal terms, "
    "search_product_terms requires an identifiable product and a question; if the product is "
    "missing or cannot be uniquely identified, ask for its name/version instead of guessing. "
    "Use own-policy tools only for factual policy summaries/status. If asked whether a claim "
    "will be paid, whether an event is covered for a real claim, or to process/track a claim, "
    "do not call an own-policy tool; explain that the Agent cannot determine claim outcomes "
    "and direct the customer to the insurer's claim channel. Own-policy tools always use the "
    "authenticated customer. Never invent IDs or send unrelated fields. Omit optional "
    "category_id when no Java category ID is available; never pass an empty string."
)


class CustomerTaskTools(Toolkit):
    """Model-facing task facade; dispatcher remains the single business-policy boundary."""

    def __init__(
        self,
        dispatcher: CustomerTaskDispatcher | None = None,
        **kwargs: Any,
    ) -> None:
        self._dispatcher = dispatcher or CustomerTaskDispatcher()
        super().__init__(
            name="customer_task_tools",
            tools=[
                self.list_catalog_categories,
                self.list_recommendation_categories,
                self.search_catalog_products,
                self.search_comparison_products,
                self.get_recommendation_criteria,
                self.recommend_products,
                self.compare_products,
                self.search_product_terms,
                self.get_my_policies,
                self.get_my_policy,
                self.get_selected_policy,
            ],
            instructions=CUSTOMER_TASK_TOOL_INSTRUCTIONS,
            add_instructions=True,
            **kwargs,
        )

    def list_catalog_categories(self, run_context: RunContext) -> dict[str, Any]:
        """List Java product categories only when the customer explicitly wants to browse.

        For a personalized recommendation use list_recommendation_categories instead.
        """
        return self._dispatch(
            run_context,
            CustomerTaskIntent.PRODUCT_CATALOG,
            CustomerTaskAction.LIST_CATEGORIES,
        )

    def list_recommendation_categories(self, run_context: RunContext) -> dict[str, Any]:
        """List categories as a prerequisite for a personalized recommendation.

        Do not use for a direct request to browse the catalog, search products, or compare products.
        """
        return self._dispatch(
            run_context,
            CustomerTaskIntent.PRODUCT_RECOMMENDATION,
            CustomerTaskAction.LIST_CATEGORIES,
        )

    def search_catalog_products(
        self,
        run_context: RunContext,
        keyword: CustomerTaskKeyword,
        category_id: OptionalProductCategoryId = None,
    ) -> dict[str, Any]:
        """Search enabled catalog products by the user's product/scenario keyword.

        Omit category_id unless Java returned a category ID; never send an empty string. The service
        returns at most three products. Do not use this for terms, recommendations, or claims.
        """
        return self._dispatch(
            run_context,
            CustomerTaskIntent.PRODUCT_CATALOG,
            CustomerTaskAction.SEARCH_PRODUCTS,
            keyword=keyword,
            category_id=category_id,
            limit=3,
        )

    def search_comparison_products(
        self,
        run_context: RunContext,
        keyword: CustomerTaskKeyword,
        category_id: OptionalProductCategoryId = None,
    ) -> dict[str, Any]:
        """First step for every request to compare products or to find products for comparison.

        Search using the user's scenario or product terms, then compare only IDs returned by this
        search. Never use a recommendation-category tool for a comparison request. Omit category_id
        unless Java returned one; never pass an empty string.
        """
        return self._dispatch(
            run_context,
            CustomerTaskIntent.PRODUCT_COMPARISON,
            CustomerTaskAction.SEARCH_PRODUCTS,
            keyword=keyword,
            category_id=category_id,
            limit=3,
        )

    def get_recommendation_criteria(
        self,
        run_context: RunContext,
        category_id: ProductCategoryId,
    ) -> dict[str, Any]:
        """Get published recommendation criteria for a Java-returned product category ID."""
        return self._dispatch(
            run_context,
            CustomerTaskIntent.PRODUCT_RECOMMENDATION,
            CustomerTaskAction.GET_RECOMMENDATION_CRITERIA,
            category_id=category_id,
        )

    def recommend_products(
        self,
        run_context: RunContext,
        criteria: ProductRecommendationCriteria,
    ) -> dict[str, Any]:
        """Evaluate customer preferences using criteria returned by get_recommendation_criteria."""
        return self._dispatch(
            run_context,
            CustomerTaskIntent.PRODUCT_RECOMMENDATION,
            CustomerTaskAction.RECOMMEND_PRODUCTS,
            criteria=criteria,
            limit=3,
        )

    def compare_products(
        self,
        run_context: RunContext,
        category_id: ProductCategoryId,
        product_ids: Annotated[list[str], Field(min_length=2, max_length=10)],
    ) -> dict[str, Any]:
        """Compare products returned by search_comparison_products in this run."""
        return self._dispatch(
            run_context,
            CustomerTaskIntent.PRODUCT_COMPARISON,
            CustomerTaskAction.COMPARE_PRODUCTS,
            category_id=category_id,
            product_ids=product_ids,
        )

    def search_product_terms(
        self,
        run_context: RunContext,
        product_name: Annotated[str, Field(min_length=1, max_length=200)],
        question: Annotated[str, Field(min_length=1, max_length=1000)],
    ) -> dict[str, Any]:
        """Search formal terms for a named product and a specific coverage question."""
        return self._dispatch(
            run_context,
            CustomerTaskIntent.TERMS_QA,
            CustomerTaskAction.SEARCH_PRODUCT_TERMS,
            product_name=product_name,
            question=question,
        )

    def get_my_policies(
        self,
        run_context: RunContext,
        raw_arguments: ProviderNullSentinel = None,
    ) -> dict[str, Any]:
        """List only the authenticated customer's policy summaries.

        The optional raw_arguments field exists only to tolerate compatible model providers that
        serialize a no-argument call as the literal string "null"; it carries no business data.
        """
        return self._dispatch(
            run_context,
            CustomerTaskIntent.OWN_POLICY,
            CustomerTaskAction.GET_MY_POLICIES,
        )

    def get_my_policy(
        self,
        run_context: RunContext,
        policy_id: PolicyId,
    ) -> dict[str, Any]:
        """Read one of the authenticated customer's policies returned in this run."""
        return self._dispatch(
            run_context,
            CustomerTaskIntent.OWN_POLICY,
            CustomerTaskAction.GET_MY_POLICY,
            policy_id=policy_id,
        )

    def get_selected_policy(self, run_context: RunContext) -> dict[str, Any]:
        """Read the policy ID selected and validated by the Java-backed chat request."""
        return self._dispatch(
            run_context,
            CustomerTaskIntent.OWN_POLICY,
            CustomerTaskAction.GET_SELECTED_POLICY,
        )

    def _dispatch(
        self,
        run_context: RunContext,
        intent: CustomerTaskIntent,
        action: CustomerTaskAction,
        **arguments: Any,
    ) -> dict[str, Any]:
        # OpenAI-compatible models sometimes serialize an omitted optional argument as "".
        # Treat that as absent so it cannot become an invalid Java category filter.
        if action == CustomerTaskAction.SEARCH_PRODUCTS:
            for name in ("category_id", "keyword"):
                value = arguments.get(name)
                if not isinstance(value, str):
                    continue
                normalized_value = value.strip()
                if not normalized_value or (
                    name == "category_id" and normalized_value.casefold() == "null"
                ):
                    arguments.pop(name)
                else:
                    arguments[name] = normalized_value
        if intent == CustomerTaskIntent.OWN_POLICY and arguments.get("raw_arguments") in (
            None,
            "null",
        ):
            arguments.pop("raw_arguments", None)
        payload = {"intent": intent, "action": action, **arguments}
        try:
            request = CustomerTaskRequest.model_validate(payload)
        except ValidationError as exc:
            return {
                "status": "INVALID_TASK_REQUEST",
                "detail": "任务参数不完整或与所选操作不匹配，请先向用户澄清。",
                "validation_errors": [
                    {"field": ".".join(str(part) for part in error["loc"])}
                    for error in exc.errors()[:8]
                ],
            }
        return self._dispatcher.dispatch(request, run_context)

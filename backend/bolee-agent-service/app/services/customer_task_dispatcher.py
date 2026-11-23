"""Validate and dispatch the single LLM-facing customer task tool."""

from __future__ import annotations

from typing import Any, Callable

from app.contexts.customer_task import CustomerTaskRuntime
from app.contexts.tool import AgentToolContext
from app.domain.customer_task import CustomerTaskStatus
from app.schemas.customer_task import CustomerTaskAction, CustomerTaskRequest
from app.schemas.product_catalog import ProductMatchType
from app.tools.insurance_terms import InsuranceTermsTools
from app.tools.own_policy import OwnPolicyTools
from app.tools.product_catalog import ProductCatalogTools
from app.tools.product_comparison import ProductComparisonTools
from app.tools.product_recommendation import ProductRecommendationTools


class CustomerTaskDispatcher:
    """Route validated actions to existing Agno adapters and Python services."""

    _HANDLERS: dict[CustomerTaskAction, tuple[str, str]] = {
        CustomerTaskAction.LIST_CATEGORIES: ("catalog", "list_product_categories"),
        CustomerTaskAction.SEARCH_PRODUCTS: ("catalog", "search_products_by_category"),
        CustomerTaskAction.GET_RECOMMENDATION_CRITERIA: (
            "recommendation",
            "get_product_recommendation_criteria",
        ),
        CustomerTaskAction.RECOMMEND_PRODUCTS: ("recommendation", "recommend_products"),
        CustomerTaskAction.COMPARE_PRODUCTS: ("comparison", "compare_products"),
        CustomerTaskAction.SEARCH_PRODUCT_TERMS: ("terms", "search_product_terms"),
        CustomerTaskAction.GET_MY_POLICIES: ("own_policy", "get_my_policies"),
        CustomerTaskAction.GET_MY_POLICY: ("own_policy", "get_my_policy"),
        CustomerTaskAction.GET_SELECTED_POLICY: ("own_policy", "get_selected_policy"),
    }
    _INTERMEDIATE_ACTIONS = {
        CustomerTaskAction.LIST_CATEGORIES,
        CustomerTaskAction.GET_RECOMMENDATION_CRITERIA,
        CustomerTaskAction.GET_MY_POLICIES,
    }
    _USER_CLARIFICATION_STATUSES = {
        "NEEDS_CLARIFICATION",
        "NEEDS_PRODUCT_CLARIFICATION",
        "NEEDS_VERSION_CLARIFICATION",
    }

    def __init__(
        self,
        *,
        catalog: ProductCatalogTools | None = None,
        recommendation: ProductRecommendationTools | None = None,
        comparison: ProductComparisonTools | None = None,
        terms: InsuranceTermsTools | None = None,
        own_policy: OwnPolicyTools | None = None,
    ) -> None:
        self._tool_adapters = {
            "catalog": catalog or ProductCatalogTools(),
            "recommendation": recommendation or ProductRecommendationTools(),
            "comparison": comparison or ProductComparisonTools(),
            "terms": terms or InsuranceTermsTools(),
            "own_policy": own_policy or OwnPolicyTools(),
        }

    def dispatch(self, request: CustomerTaskRequest, run_context: Any) -> dict[str, Any]:
        from app.tools.runtime import resolve_tool_context

        tool_context = resolve_tool_context(run_context)
        runtime = tool_context.customer_task_runtime
        if not isinstance(runtime, CustomerTaskRuntime):
            return self._outcome(request, "INVALID_RUNTIME", "当前任务状态不可用。")

        with runtime.lock:
            if runtime.claimed_intent is not None and runtime.claimed_intent != request.intent:
                return self._outcome(
                    request,
                    "TASK_CONFLICT",
                    "本轮已开始处理另一个主要任务。请先完成当前任务，或请用户分开提出另一个请求。",
                    runtime.active_task.task_id,
                    runtime.active_task.stage,
                )

            if runtime.claimed_intent is None:
                runtime.claimed_intent = request.intent
                if runtime.active_task.intent != request.intent:
                    from app.domain.customer_task import ActiveTaskState

                    runtime.active_task = ActiveTaskState(
                        intent=request.intent,
                        status=CustomerTaskStatus.IN_PROGRESS,
                        stage="STARTED",
                    )

            blocked = self._validate_workflow(request, tool_context, runtime)
            if blocked is not None:
                return self._outcome(
                    request,
                    "BLOCKED_BY_WORKFLOW",
                    blocked,
                    runtime.active_task.task_id,
                    runtime.active_task.stage,
                )

            adapter_name, method_name = self._HANDLERS[request.action]
            adapter = self._tool_adapters[adapter_name]
            handler: Callable[..., Any] = getattr(adapter, method_name)
            result = handler(run_context, **request.handler_arguments())
            runtime.actions_this_run.add(request.action)
            state_status, missing_slots = self._task_status(request, result)
            runtime.active_task = runtime.active_task.transition(
                intent=request.intent,
                action=request.action.value,
                status=state_status,
                stage=request.action.value,
                slots=self._safe_slots(request),
                missing_slots=missing_slots,
            )
            return {
                "status": "TASK_ACTION_COMPLETED",
                "intent": request.intent.value,
                "action": request.action.value,
                "task_id": runtime.active_task.task_id,
                "task_status": runtime.active_task.status.value,
                "stage": runtime.active_task.stage,
                "missing_slots": list(runtime.active_task.missing_slots),
                "result": result,
            }

    def _validate_workflow(
        self,
        request: CustomerTaskRequest,
        context: AgentToolContext,
        runtime: CustomerTaskRuntime,
    ) -> str | None:
        if (
            request.action == CustomerTaskAction.RECOMMEND_PRODUCTS
            and CustomerTaskAction.GET_RECOMMENDATION_CRITERIA not in runtime.actions_this_run
        ):
            return "推荐前必须先读取 Java 当前发布的条件目录，并按该目录组织参数。"

        if request.action == CustomerTaskAction.COMPARE_PRODUCTS:
            if CustomerTaskAction.SEARCH_PRODUCTS not in runtime.actions_this_run:
                return "比较前必须先通过 Java 产品目录确认本轮比较对象。"
            catalog_result = context.result_collector.product_catalog_search
            returned_ids = {
                product.product_id for product in catalog_result.candidates
            } if catalog_result is not None else set()
            if not request.product_ids or not set(request.product_ids).issubset(returned_ids):
                return "比较对象必须来自本轮 Java 产品目录结果。"
            if catalog_result is not None and any(
                product.category_id != request.category_id
                for product in catalog_result.candidates
                if product.product_id in request.product_ids
            ):
                return "比较产品类别必须与 Java 产品目录结果一致。"

        if request.action == CustomerTaskAction.GET_MY_POLICY:
            if CustomerTaskAction.GET_MY_POLICIES not in runtime.actions_this_run:
                return "查询单份本人保单前必须先读取当前登录人的保单列表。"
            summary = context.result_collector.own_policy_summary
            returned_ids = {
                policy.policy_id for policy in summary.policies
            } if summary is not None else set()
            if request.policy_id not in returned_ids:
                return "保单 ID 必须来自本轮 Java 返回的本人保单列表。"

        if (
            request.action == CustomerTaskAction.GET_SELECTED_POLICY
            and not context.selected_policy_id
        ):
            return "当前请求没有经过 Java 校验的已选保单。"
        return None

    @classmethod
    def _task_status(
        cls, request: CustomerTaskRequest, result: Any
    ) -> tuple[CustomerTaskStatus, tuple[str, ...]]:
        action = request.action
        result_status = result.get("status") if isinstance(result, dict) else None
        if result_status in cls._USER_CLARIFICATION_STATUSES:
            missing = result.get("missingCriteria", result.get("missing_slots", []))
            if isinstance(missing, list):
                return CustomerTaskStatus.WAITING_FOR_USER, tuple(
                    str(item)[:64] for item in missing[:8]
                )
            return CustomerTaskStatus.WAITING_FOR_USER, ()
        if action == CustomerTaskAction.SEARCH_PRODUCTS and isinstance(result, dict):
            candidates = result.get("candidates", [])
            if any(
                isinstance(candidate, dict)
                and candidate.get("matchType", candidate.get("match_type"))
                == ProductMatchType.RELATED.value
                for candidate in candidates
            ):
                return CustomerTaskStatus.WAITING_FOR_USER, ("PRODUCT_CONFIRMATION",)
        if action in cls._INTERMEDIATE_ACTIONS or (
            action == CustomerTaskAction.SEARCH_PRODUCTS
            and request.intent.value == "PRODUCT_COMPARISON"
        ):
            return CustomerTaskStatus.IN_PROGRESS, ()
        return CustomerTaskStatus.COMPLETED, ()

    @staticmethod
    def _safe_slots(request: CustomerTaskRequest) -> tuple[tuple[str, str], ...]:
        allowed = {"category_id", "keyword", "product_name"}
        values: list[tuple[str, str]] = []
        for field_name in allowed:
            value = getattr(request, field_name)
            if isinstance(value, str) and value:
                values.append((field_name, value[:200]))
        if request.keywords:
            values.append(("keywords", ", ".join(request.keywords)[:200]))
        return tuple(values[:8])

    @staticmethod
    def _outcome(
        request: CustomerTaskRequest,
        status: str,
        detail: str,
        task_id: str = "",
        stage: str = "BLOCKED",
    ) -> dict[str, Any]:
        return {
            "status": status,
            "intent": request.intent.value,
            "action": request.action.value,
            "task_id": task_id,
            "task_status": "IN_PROGRESS",
            "stage": stage,
            "missing_slots": [],
            "detail": detail[:500],
        }

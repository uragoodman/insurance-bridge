"""Validate and dispatch the single LLM-facing customer task tool."""

from __future__ import annotations

from typing import Any, Callable

from app.contexts.customer_task import CustomerTaskRuntime
from app.domain.customer_task import CustomerTaskStatus
from app.schemas.customer_task import CustomerTaskAction, CustomerTaskRequest
from app.schemas.product_catalog import ProductMatchType
from app.services.customer_task_policy import CustomerTaskExecutionPolicy
from app.tools.insurance_terms import InsuranceTermsTools
from app.tools.own_policy import OwnPolicyTools
from app.tools.product_catalog import ProductCatalogTools
from app.tools.product_comparison import ProductComparisonTools
from app.tools.product_recommendation import ProductRecommendationTools


class CustomerTaskDispatcher:
    """将校验通过的任务动作分发给对应业务适配器。"""

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
        policy: CustomerTaskExecutionPolicy | None = None,
    ) -> None:
        self._tool_adapters = {
            "catalog": catalog or ProductCatalogTools(),
            "recommendation": recommendation or ProductRecommendationTools(),
            "comparison": comparison or ProductComparisonTools(),
            "terms": terms or InsuranceTermsTools(),
            "own_policy": own_policy or OwnPolicyTools(),
        }
        self._policy = policy or CustomerTaskExecutionPolicy()

    def dispatch(self, request: CustomerTaskRequest, run_context: Any) -> dict[str, Any]:
        # 顺序：取得本轮任务状态 -> 执行策略检查 -> 调用适配器 -> 更新任务状态 -> 返回结果给 Agent。
        from app.tools.runtime import resolve_tool_context

        tool_context = resolve_tool_context(run_context)
        runtime = tool_context.customer_task_runtime
        if not isinstance(runtime, CustomerTaskRuntime):
            return self._outcome(request, "INVALID_RUNTIME", "当前任务状态不可用。")

        with runtime.lock:
            decision = self._policy.evaluate(request, tool_context, runtime)
            if decision is not None:
                return self._outcome(
                    request,
                    decision.status,
                    decision.detail,
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

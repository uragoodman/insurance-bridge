"""Deterministic execution policy for model-selected customer task actions."""

from dataclasses import dataclass

from app.contexts.customer_task import CustomerTaskRuntime
from app.contexts.tool import AgentToolContext
from app.domain.customer_task import CustomerTaskIntent
from app.schemas.customer_task import CustomerTaskAction, CustomerTaskRequest


@dataclass(frozen=True, slots=True)
class CustomerTaskPolicyDecision:
    status: str
    detail: str


class CustomerTaskExecutionPolicy:
    """在业务调用前确定性检查意图允许的动作和任务前置条件。"""

    _ALLOWED_ACTIONS: dict[CustomerTaskIntent, frozenset[CustomerTaskAction]] = {
        CustomerTaskIntent.PRODUCT_CATALOG: frozenset(
            {CustomerTaskAction.LIST_CATEGORIES, CustomerTaskAction.SEARCH_PRODUCTS}
        ),
        CustomerTaskIntent.PRODUCT_RECOMMENDATION: frozenset(
            {
                CustomerTaskAction.LIST_CATEGORIES,
                CustomerTaskAction.GET_RECOMMENDATION_CRITERIA,
                CustomerTaskAction.RECOMMEND_PRODUCTS,
            }
        ),
        CustomerTaskIntent.PRODUCT_COMPARISON: frozenset(
            {
                CustomerTaskAction.LIST_CATEGORIES,
                CustomerTaskAction.SEARCH_PRODUCTS,
                CustomerTaskAction.COMPARE_PRODUCTS,
            }
        ),
        CustomerTaskIntent.TERMS_QA: frozenset(
            {CustomerTaskAction.SEARCH_PRODUCT_TERMS}
        ),
        CustomerTaskIntent.OWN_POLICY: frozenset(
            {
                CustomerTaskAction.GET_MY_POLICIES,
                CustomerTaskAction.GET_MY_POLICY,
                CustomerTaskAction.GET_SELECTED_POLICY,
            }
        ),
    }

    def evaluate(
        self,
        request: CustomerTaskRequest,
        context: AgentToolContext,
        runtime: CustomerTaskRuntime,
    ) -> CustomerTaskPolicyDecision | None:
        """意图、任务流程或证据不符合要求时，返回拦截结果；符合时返回 None。"""
        allowed_actions = self._ALLOWED_ACTIONS.get(request.intent, frozenset())
        if request.action not in allowed_actions:
            return CustomerTaskPolicyDecision(
                "BLOCKED_BY_POLICY", "所选任务不允许执行此业务操作。"
            )

        if runtime.claimed_intent is not None and runtime.claimed_intent != request.intent:
            return CustomerTaskPolicyDecision(
                "TASK_CONFLICT",
                "本轮已开始处理另一个主要任务。请先完成当前任务，或请用户分开提出另一个请求。",
            )

        if (
            request.action == CustomerTaskAction.RECOMMEND_PRODUCTS
            and CustomerTaskAction.GET_RECOMMENDATION_CRITERIA not in runtime.actions_this_run
        ):
            return CustomerTaskPolicyDecision(
                "BLOCKED_BY_WORKFLOW",
                "推荐前必须先读取 Java 当前发布的条件目录，并按该目录组织参数。",
            )

        if request.action == CustomerTaskAction.COMPARE_PRODUCTS:
            if CustomerTaskAction.SEARCH_PRODUCTS not in runtime.actions_this_run:
                return CustomerTaskPolicyDecision(
                    "BLOCKED_BY_WORKFLOW", "比较前必须先通过 Java 产品目录确认本轮比较对象。"
                )
            catalog_result = context.result_collector.product_catalog_search
            returned_ids = {
                product.product_id for product in catalog_result.candidates
            } if catalog_result is not None else set()
            if not request.product_ids or not set(request.product_ids).issubset(returned_ids):
                return CustomerTaskPolicyDecision(
                    "BLOCKED_BY_WORKFLOW", "比较对象必须来自本轮 Java 产品目录结果。"
                )
            if catalog_result is not None and any(
                product.category_id != request.category_id
                for product in catalog_result.candidates
                if product.product_id in request.product_ids
            ):
                return CustomerTaskPolicyDecision(
                    "BLOCKED_BY_WORKFLOW", "比较产品类别必须与 Java 产品目录结果一致。"
                )

        if request.action == CustomerTaskAction.GET_MY_POLICY:
            if CustomerTaskAction.GET_MY_POLICIES not in runtime.actions_this_run:
                return CustomerTaskPolicyDecision(
                    "BLOCKED_BY_WORKFLOW", "查询单份本人保单前必须先读取当前登录人的保单列表。"
                )
            summary = context.result_collector.own_policy_summary
            returned_ids = {
                policy.policy_id for policy in summary.policies
            } if summary is not None else set()
            if request.policy_id not in returned_ids:
                return CustomerTaskPolicyDecision(
                    "BLOCKED_BY_WORKFLOW", "保单 ID 必须来自本轮 Java 返回的本人保单列表。"
                )

        if (
            request.action == CustomerTaskAction.GET_SELECTED_POLICY
            and not context.selected_policy_id
        ):
            return CustomerTaskPolicyDecision(
                "BLOCKED_BY_WORKFLOW", "当前请求没有经过 Java 校验的已选保单。"
            )
        return None


__all__ = ["CustomerTaskExecutionPolicy", "CustomerTaskPolicyDecision"]

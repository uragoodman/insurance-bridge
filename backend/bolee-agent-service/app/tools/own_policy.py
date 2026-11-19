"""Agno tool for the current authenticated customer's own policy summary."""

from typing import Any

from agno.run import RunContext
from agno.tools import Toolkit

from app.services.own_policy import OwnPolicyService, OwnPolicyServiceError
from app.tools.payload import bounded_tool_payload
from app.tools.runtime import AgentToolContextError, resolve_tool_context


class OwnPolicyTools(Toolkit):
    def __init__(self, service: OwnPolicyService | None = None, **kwargs: Any) -> None:
        self._service = service or OwnPolicyService()
        super().__init__(
            name="insurance_own_policy",
            tools=[self.get_my_policies, self.get_my_policy, self.get_selected_policy],
            instructions=(
                "Use this tool only when the customer explicitly asks about their own policies, "
                "such as policy status or coverage dates. It always queries the authenticated "
                "customer; never ask for or pass a user ID, policy number, identity number, or "
                "family member ID. Query one policy only by using its policy_id returned from "
                "get_my_policies. Only use returned fields. This tool does not provide policy "
                "wording, premium quotes, payment history, claims, or underwriting decisions. "
                "When the chat request includes a Java-validated selected policy, use "
                "get_selected_policy to explain only its returned summary."
            ),
            add_instructions=True,
            **kwargs,
        )

    def get_my_policies(self, run_context: RunContext) -> dict[str, Any]:
        """Query the current authenticated customer's active policy summaries."""
        try:
            tool_context = resolve_tool_context(run_context)
            result = self._service.get_my_policies(tool_context)
        except (AgentToolContextError, OwnPolicyServiceError) as exc:
            raise RuntimeError(str(exc)) from exc
        return bounded_tool_payload(
            result.model_dump(mode="json"), tool_context.result_collector
        )

    def get_my_policy(self, run_context: RunContext, policy_id: str) -> dict[str, Any]:
        """Query one policy using its policy_id from the authenticated policy list."""
        try:
            tool_context = resolve_tool_context(run_context)
            result = self._service.get_my_policy(policy_id, tool_context)
        except (AgentToolContextError, OwnPolicyServiceError) as exc:
            raise RuntimeError(str(exc)) from exc
        return bounded_tool_payload(
            result.model_dump(mode="json"), tool_context.result_collector
        )

    def get_selected_policy(self, run_context: RunContext) -> dict[str, Any]:
        """Query the policy selected in the Java-authenticated chat request."""
        try:
            tool_context = resolve_tool_context(run_context)
            if not tool_context.selected_policy_id:
                raise OwnPolicyServiceError("No policy has been selected.")
            result = self._service.get_my_policy(tool_context.selected_policy_id, tool_context)
        except (AgentToolContextError, OwnPolicyServiceError) as exc:
            raise RuntimeError(str(exc)) from exc
        return bounded_tool_payload(
            result.model_dump(mode="json"), tool_context.result_collector
        )

"""Validated read-only use case for querying the authenticated customer's policies."""

import logging

from pydantic import ValidationError

from app.clients.java_internal_api import JavaApiError
from app.clients.own_policy import OwnPolicyJavaClient
from app.contexts.tool import AgentToolContext
from app.schemas.java import JavaResponseEnvelope
from app.schemas.own_policy import OwnPolicyDetailResponse, OwnPolicySummaryResponse

logger = logging.getLogger("bolee-agent.own-policy")


class OwnPolicyServiceError(RuntimeError):
    """Sanitized own-policy failure safe to expose to the Agent."""


class OwnPolicyService:
    _JAVA_SUCCESS_CODE = 200

    def get_my_policies(self, context: AgentToolContext) -> OwnPolicySummaryResponse:
        context.result_collector.clear()
        try:
            raw = OwnPolicyJavaClient(context.java_internal_api).get_summary(
                context.authorization_context
            )
            envelope = JavaResponseEnvelope[OwnPolicySummaryResponse].model_validate(raw)
        except JavaApiError as exc:
            logger.warning("Own-policy query failed")
            raise OwnPolicyServiceError(
                "Your policy information is temporarily unavailable."
            ) from exc
        except ValidationError as exc:
            logger.warning("Invalid Java own-policy response contract")
            raise OwnPolicyServiceError("The policy summary response is invalid.") from exc

        if envelope.code != self._JAVA_SUCCESS_CODE or envelope.data is None:
            logger.warning("Java own-policy envelope rejected; code=%s", envelope.code)
            raise OwnPolicyServiceError("Your policy information is temporarily unavailable.")

        context.result_collector.record_own_policy_summary(envelope.data)
        return envelope.data

    def get_my_policy(self, policy_id: str, context: AgentToolContext) -> OwnPolicyDetailResponse:
        context.result_collector.clear()
        try:
            raw = OwnPolicyJavaClient(context.java_internal_api).get_policy(
                policy_id,
                context.authorization_context,
            )
        except ValueError as exc:
            raise OwnPolicyServiceError("Select a policy from your returned policy list.") from exc
        except JavaApiError as exc:
            logger.warning("Own-policy detail query failed")
            raise OwnPolicyServiceError(
                "Your policy information is temporarily unavailable."
            ) from exc
        try:
            envelope = JavaResponseEnvelope[OwnPolicyDetailResponse].model_validate(raw)
        except ValidationError as exc:
            logger.warning("Invalid Java own-policy detail response contract")
            raise OwnPolicyServiceError("The policy detail response is invalid.") from exc

        if envelope.code != self._JAVA_SUCCESS_CODE or envelope.data is None:
            logger.warning("Java own-policy detail envelope rejected; code=%s", envelope.code)
            raise OwnPolicyServiceError("Your policy information is temporarily unavailable.")
        if envelope.data.policy is not None and envelope.data.policy.policy_id != policy_id:
            logger.warning("Java own-policy detail returned a mismatched policy reference")
            raise OwnPolicyServiceError("The policy detail response is invalid.")
        context.result_collector.record_own_policy_detail(envelope.data)
        return envelope.data

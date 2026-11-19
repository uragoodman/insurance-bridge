"""Typed contract for the current customer's own-policy summary tool."""

from enum import StrEnum

from pydantic import AliasChoices, Field, model_validator

from app.schemas.base import ContractModel


class OwnPolicyQueryStatus(StrEnum):
    FOUND = "FOUND"
    NO_ACTIVE_POLICIES = "NO_ACTIVE_POLICIES"
    PROFILE_NOT_LINKED = "PROFILE_NOT_LINKED"


class OwnPolicyStatus(StrEnum):
    PENDING_PAYMENT = "PENDING_PAYMENT"
    PENDING_EFFECTIVE = "PENDING_EFFECTIVE"
    IN_FORCE = "IN_FORCE"


class OwnPolicySummary(ContractModel):
    policy_id: str = Field(
        validation_alias=AliasChoices("policy_id", "policyId"),
        min_length=1,
        max_length=19,
        pattern=r"^[0-9]+$",
    )
    product_name: str | None = Field(
        default=None,
        validation_alias=AliasChoices("product_name", "productName"),
        max_length=200,
    )
    policy_status: OwnPolicyStatus = Field(
        validation_alias=AliasChoices("policy_status", "policyStatus")
    )
    policy_status_label: str = Field(
        validation_alias=AliasChoices("policy_status_label", "policyStatusLabel"),
        min_length=1,
        max_length=40,
    )
    coverage_start_time: str | None = Field(
        default=None,
        validation_alias=AliasChoices("coverage_start_time", "coverageStartTime"),
        max_length=40,
    )
    coverage_end_time: str | None = Field(
        default=None,
        validation_alias=AliasChoices("coverage_end_time", "coverageEndTime"),
        max_length=40,
    )
    auto_extension_enabled: bool | None = Field(
        default=None,
        validation_alias=AliasChoices("auto_extension_enabled", "autoExtensionEnabled"),
    )


class OwnPolicySummaryResponse(ContractModel):
    status: OwnPolicyQueryStatus
    as_of: str = Field(validation_alias=AliasChoices("as_of", "asOf"), min_length=1, max_length=40)
    policies: list[OwnPolicySummary] = Field(default_factory=list, max_length=20)
    returned_count: int = Field(
        validation_alias=AliasChoices("returned_count", "returnedCount"), ge=0, le=20
    )
    truncated: bool

    @model_validator(mode="after")
    def validate_result_state(self) -> "OwnPolicySummaryResponse":
        if self.returned_count != len(self.policies):
            raise ValueError("Own-policy count does not match the returned records")
        if self.status == OwnPolicyQueryStatus.FOUND and not self.policies:
            raise ValueError("FOUND requires at least one policy")
        if self.status in {
            OwnPolicyQueryStatus.NO_ACTIVE_POLICIES,
            OwnPolicyQueryStatus.PROFILE_NOT_LINKED,
        } and (self.policies or self.truncated):
            raise ValueError("Empty policy statuses cannot include policies")
        if self.truncated and self.returned_count != 20:
            raise ValueError("Truncated policy results must contain the maximum page size")
        return self


class OwnPolicyDetailStatus(StrEnum):
    FOUND = "FOUND"
    NOT_FOUND = "NOT_FOUND"


class OwnPolicyDetailResponse(ContractModel):
    status: OwnPolicyDetailStatus
    as_of: str = Field(validation_alias=AliasChoices("as_of", "asOf"), min_length=1, max_length=40)
    policy: OwnPolicySummary | None = None

    @model_validator(mode="after")
    def validate_result_state(self) -> "OwnPolicyDetailResponse":
        if (self.status == OwnPolicyDetailStatus.FOUND) != (self.policy is not None):
            raise ValueError("Policy detail status does not match the result")
        return self

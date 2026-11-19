"""Schemas for Java's standard ResponseResult envelope."""

from typing import Generic, TypeVar

from pydantic import ConfigDict, Field

from app.schemas.base import ContractModel

T = TypeVar("T")


class JavaResponseEnvelope(ContractModel, Generic[T]):
    model_config = ConfigDict(
        extra="ignore",
        populate_by_name=True,
        str_strip_whitespace=True,
    )

    code: int = Field(strict=True)
    msg: str | None = None
    data: T | None = None

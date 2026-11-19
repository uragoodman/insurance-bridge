"""Operator-only API contracts for generating product search tags."""

from typing import Literal

from pydantic import Field, field_validator, model_validator

from app.schemas.base import ContractModel


class ProductSearchTagInput(ContractModel):
    product_name: str = Field(min_length=1, max_length=200)
    category_name: str | None = Field(default=None, max_length=200)
    product_labels: str | None = Field(default=None, max_length=4000)
    application_notes: str | None = Field(default=None, max_length=4000)
    product_review: str | None = Field(default=None, max_length=4000)
    product_description: str | None = Field(default=None, max_length=4000)

    @model_validator(mode="after")
    def has_source_content(self) -> "ProductSearchTagInput":
        if not any(
            value and value.strip()
            for value in (
                self.product_labels,
                self.application_notes,
                self.product_review,
                self.product_description,
            )
        ):
            raise ValueError("Product descriptions are required to generate tags")
        return self


class GeneratedProductSearchTag(ContractModel):
    tag_type: Literal["PRODUCT_TYPE", "AUDIENCE", "SCENARIO", "KEYWORD"]
    tag_value: str = Field(min_length=1, max_length=128)
    evidence: str = Field(min_length=1, max_length=512)

    @field_validator("tag_value", "evidence")
    @classmethod
    def non_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Tag value and evidence cannot be blank")
        return value.strip()


class ProductSearchTagGenerationOutput(ContractModel):
    tags: list[GeneratedProductSearchTag] = Field(max_length=20)
    model_id: str = Field(min_length=1, max_length=128)
    prompt_version: str = Field(min_length=1, max_length=32)

"""Private, customer-visible history contracts between Java and Agno."""

from datetime import datetime
from enum import Enum

from pydantic import Field

from app.schemas.base import ContractModel


class HistoryMessageRole(str, Enum):
    USER = "USER"
    ASSISTANT = "ASSISTANT"


class AgentHistoryMessage(ContractModel):
    message_id: str = Field(min_length=1, max_length=64)
    run_id: str = Field(min_length=1, max_length=64)
    role: HistoryMessageRole
    content: str = Field(min_length=1, max_length=12000)
    created_at: datetime


class AgentHistoryPage(ContractModel):
    messages: list[AgentHistoryMessage] = Field(default_factory=list, max_length=100)
    next_cursor: str | None = Field(default=None, max_length=32)
    has_more: bool = False


class AgentSessionPreviewsRequest(ContractModel):
    session_ids: list[str] = Field(min_length=1, max_length=50)


class AgentSessionPreview(ContractModel):
    session_id: str = Field(min_length=1, max_length=64)
    title: str = Field(default="新对话", max_length=120)
    preview: str = Field(default="", max_length=240)


class AgentSessionPreviewsResponse(ContractModel):
    sessions: list[AgentSessionPreview] = Field(default_factory=list, max_length=50)

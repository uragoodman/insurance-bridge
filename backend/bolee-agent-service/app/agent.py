"""Insurance customer-service agent definition."""

from agno.agent import Agent
from agno.models.openai.like import OpenAILike

from app.agent_instructions import AGENT_INSTRUCTIONS
from app.config import OPENAI_COMPATIBLE_ROLE_MAP, settings
from app.tools.registry import build_agent_tools
from db import get_postgres_db

insurance_agent = Agent(
    id="insurance-customer-service",
    name="保险客服助手",
    model=OpenAILike(
        id=settings.model_id,
        api_key=settings.model_api_key or "not-configured",
        base_url=settings.model_base_url or None,
        role_map=OPENAI_COMPATIBLE_ROLE_MAP,
    ),
    tools=build_agent_tools(),
    db=get_postgres_db(),
    add_history_to_context=False,
    add_session_state_to_context=True,
    markdown=True,
    instructions=list(AGENT_INSTRUCTIONS),
)

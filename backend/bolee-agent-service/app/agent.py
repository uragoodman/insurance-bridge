"""Insurance customer-service agent definition."""

from agno.agent import Agent
from agno.models.openai.like import OpenAILike

from app.agent_instructions import AGENT_INSTRUCTIONS
from app.config import OPENAI_COMPATIBLE_ROLE_MAP, settings
from app.tools.registry import build_agent_tools
from db import get_postgres_db

insurance_agent = Agent(
    # 这是面向客户的 Agno Agent。意图识别由模型根据下方指令和可用工具完成，
    # 本项目没有单独的意图分类模型；模型选中的工具随后由 Python 工具层校验和分发。
    id="insurance-customer-service",
    name="保险客服助手",
    model=OpenAILike(
        id=settings.model_id,
        api_key=settings.model_api_key or "not-configured",
        base_url=settings.model_base_url or None,
        role_map=OPENAI_COMPATIBLE_ROLE_MAP,
    ),
    # 只向模型开放统一的客户任务工具集，具体业务适配器不会直接暴露给模型。
    tools=build_agent_tools(),
    db=get_postgres_db(),
    add_history_to_context=False,
    add_session_state_to_context=True,
    markdown=True,
    # Agent 的业务规则提示词，例如产品查询、条款问答和本人保单查询的路由规则。
    instructions=list(AGENT_INSTRUCTIONS),
)

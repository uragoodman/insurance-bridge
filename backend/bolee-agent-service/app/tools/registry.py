"""Allowlist of model-facing Agno tools."""

from agno.tools import Toolkit

from app.tools.customer_task import CustomerTaskTools


def build_agent_tools() -> list[Toolkit]:
    # 注册给大模型的工具白名单。模型只能从这里选择工具；具体业务工具由 Dispatcher 内部调用。
    # Expose only one typed task entry point to the model. Existing business
    # toolkits are instantiated internally by CustomerTaskDispatcher.
    return [CustomerTaskTools()]

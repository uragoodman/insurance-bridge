"""Allowlist of model-facing Agno tools."""

from agno.tools import Toolkit

from app.tools.customer_task import CustomerTaskTools


def build_agent_tools() -> list[Toolkit]:
    # Expose only one typed task entry point to the model. Existing business
    # toolkits are instantiated internally by CustomerTaskDispatcher.
    return [CustomerTaskTools()]

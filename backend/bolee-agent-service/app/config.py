"""Environment-backed settings for the Agno AgentOS service."""

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    model_id: str = os.getenv("AGNO_MODEL_ID", "deepseek-chat")
    model_base_url: str = os.getenv("AGNO_MODEL_BASE_URL", "https://api.deepseek.com/v1")
    model_api_key: str = os.getenv("AGNO_MODEL_API_KEY", "")
    oauth_token_url: str = os.getenv(
        "AGNO_OAUTH_TOKEN_URL", "http://host.docker.internal:7078/oauth/service-token"
    )
    oauth_client_id: str = os.getenv("AGNO_OAUTH_CLIENT_ID", "")
    oauth_client_secret: str = os.getenv("AGNO_OAUTH_CLIENT_SECRET", "")
    oauth_scope: str = os.getenv(
        "AGNO_OAUTH_SCOPE", "product.catalog.read policy.own.read"
    )
    oauth_timeout_seconds: float = float(os.getenv("AGNO_OAUTH_TIMEOUT_SECONDS", "5"))
    oauth_token_key_url: str = os.getenv(
        "AGNO_OAUTH_TOKEN_KEY_URL", "http://host.docker.internal:7078/oauth/token_key"
    )
    oauth_chat_client_id: str = os.getenv("AGNO_CHAT_OAUTH_CLIENT_ID", "")
    oauth_operator_client_id: str = os.getenv("AGNO_OPERATOR_OAUTH_CLIENT_ID", "")
    oauth_chat_scope: str = os.getenv("AGNO_CHAT_OAUTH_SCOPE", "agent.chat")
    oauth_operator_scope: str = os.getenv("AGNO_OPERATOR_OAUTH_SCOPE", "agent.operator")
    oauth_tag_generation_client_id: str = os.getenv("AGNO_TAG_GENERATION_CLIENT_ID", "")
    oauth_tag_generation_scope: str = os.getenv(
        "AGNO_TAG_GENERATION_SCOPE", "product.tags.generate"
    )
    db_host: str = os.getenv("AGNO_DB_HOST", "127.0.0.1")
    db_port: int = int(os.getenv("AGNO_DB_PORT", "15433"))
    db_user: str = os.getenv("AGNO_DB_USER", "ai")
    db_password: str = os.getenv("AGNO_DB_PASSWORD", "ai")
    db_name: str = os.getenv("AGNO_DB_NAME", "agentos")
    java_service_url: str = os.getenv("AGNO_JAVA_SERVICE_URL", "http://host.docker.internal:17065")
    java_http_timeout_seconds: float = float(os.getenv("AGNO_JAVA_HTTP_TIMEOUT_SECONDS", "10"))
    context_window_tokens: int = int(os.getenv("AGNO_CONTEXT_WINDOW_TOKENS", "32768"))
    context_fixed_tokens: int = int(os.getenv("AGNO_CONTEXT_FIXED_TOKENS", "6000"))
    context_output_reserve_tokens: int = int(
        os.getenv("AGNO_CONTEXT_OUTPUT_RESERVE_TOKENS", "4096")
    )
    context_safety_margin_tokens: int = int(
        os.getenv("AGNO_CONTEXT_SAFETY_MARGIN_TOKENS", "2048")
    )
    context_tool_result_reserve_tokens: int = int(
        os.getenv("AGNO_CONTEXT_TOOL_RESULT_RESERVE_TOKENS", "8192")
    )
    context_soft_limit_ratio: float = float(
        os.getenv("AGNO_CONTEXT_SOFT_LIMIT_RATIO", "0.75")
    )
    context_recent_turns: int = int(os.getenv("AGNO_CONTEXT_RECENT_TURNS", "6"))
    context_keep_recent_turns: int = int(
        os.getenv("AGNO_CONTEXT_KEEP_RECENT_TURNS", "2")
    )
    context_summary_max_chars: int = int(
        os.getenv("AGNO_CONTEXT_SUMMARY_MAX_CHARS", "4000")
    )
    context_summary_model_id: str = os.getenv("AGNO_CONTEXT_SUMMARY_MODEL_ID", "")
    tool_result_max_tokens: int = int(os.getenv("AGNO_TOOL_RESULT_MAX_TOKENS", "1200"))
    context_session_lock_timeout_seconds: float = float(
        os.getenv("AGNO_CONTEXT_SESSION_LOCK_TIMEOUT_SECONDS", "30")
    )

    def __post_init__(self) -> None:
        if self.tool_result_max_tokens <= 0:
            raise ValueError("AGNO_TOOL_RESULT_MAX_TOKENS must be positive")
        if self.context_tool_result_reserve_tokens < self.tool_result_max_tokens:
            raise ValueError(
                "AGNO_CONTEXT_TOOL_RESULT_RESERVE_TOKENS must cover at least one tool result"
            )


settings = Settings()

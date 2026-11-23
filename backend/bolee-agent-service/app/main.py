"""FastAPI entry point and protected AgentOS runtime."""

import logging
import re

from agno.models.openai.like import OpenAILike
from agno.os import AgentOS
from fastapi import FastAPI, Header, HTTPException, Query, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

from app.agent import insurance_agent
from app.clients.java_internal_api import JavaInternalApiClient
from app.clients.oauth2 import OAuth2ClientCredentials
from app.clients.oauth2_jwt import OAuth2JWTValidator
from app.config import OPENAI_COMPATIBLE_ROLE_MAP, settings
from app.contexts.agent import AgentDependencies
from app.contexts.tool import AgentToolResultCollector
from app.domain.conversation_context import (
    ContextBudget,
    ContextBudgetPolicy,
    SessionTurnInProgressError,
)
from app.infrastructure.agno_agent_runner import AgnoAgentRunner
from app.infrastructure.agno_context import AgnoSessionContextStore, AgnoSummaryGenerator
from app.infrastructure.postgres_session_lock import PostgresSessionTurnLock
from app.schemas import ChatRequest, ChatResponse, RecommendationStatus
from app.schemas.history import (
    AgentHistoryPage,
    AgentSessionPreviewsRequest,
    AgentSessionPreviewsResponse,
)
from app.schemas.product_search_tags import (
    ProductSearchTagGenerationOutput,
    ProductSearchTagInput,
)
from app.security.oauth_scope_middleware import (
    AgentOSOAuthScopeMiddleware,
    AgentOSRouteScopePolicy,
)
from app.services.chat_application import ContextAwareChatApplicationService
from app.services.context_management import (
    ContextCompactionUnavailableError,
    ContextInputTooLargeError,
    ContextManagementService,
)
from app.services.history import get_session_history, get_session_previews
from app.services.product_search_tags import (
    ProductSearchTagGenerationError,
    generate_product_search_tags,
)
from app.services.retention import (
    delete_expired_sessions,
    delete_expired_traces,
    delete_session_and_traces,
)
from db import get_postgres_db

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("bolee-agent")
java_internal_api = JavaInternalApiClient(
    base_url=settings.java_service_url,
    oauth_client=OAuth2ClientCredentials(
        token_url=settings.oauth_token_url,
        client_id=settings.oauth_client_id,
        client_secret=settings.oauth_client_secret,
        scope=settings.oauth_scope,
        timeout_seconds=settings.oauth_timeout_seconds,
    ),
    timeout_seconds=settings.java_http_timeout_seconds,
)
oauth_jwt_validator = OAuth2JWTValidator(
    token_key_url=settings.oauth_token_key_url,
    expected_client_ids={
        settings.oauth_chat_scope: settings.oauth_chat_client_id,
        settings.oauth_operator_scope: settings.oauth_operator_client_id,
        settings.oauth_tag_generation_scope: settings.oauth_tag_generation_client_id,
    },
)
context_budget_policy = ContextBudgetPolicy(
    ContextBudget(
        context_window_tokens=settings.context_window_tokens,
        fixed_prompt_tokens=settings.context_fixed_tokens,
        output_reserve_tokens=settings.context_output_reserve_tokens,
        safety_margin_tokens=settings.context_safety_margin_tokens,
        soft_limit_ratio=settings.context_soft_limit_ratio,
        tool_result_reserve_tokens=settings.context_tool_result_reserve_tokens,
    ),
    recent_turn_limit=settings.context_recent_turns,
)
context_store = AgnoSessionContextStore(
    agent_provider=lambda: insurance_agent,
    summary_max_chars=settings.context_summary_max_chars,
)
context_management = ContextManagementService(
    store=context_store,
    summary_generator=AgnoSummaryGenerator(
        OpenAILike(
            id=settings.context_summary_model_id or settings.model_id,
            api_key=settings.model_api_key or "not-configured",
            base_url=settings.model_base_url or None,
            role_map=OPENAI_COMPATIBLE_ROLE_MAP,
            max_tokens=768,
            temperature=0,
        )
    ),
    budget_policy=context_budget_policy,
    summary_max_chars=settings.context_summary_max_chars,
    recent_turn_limit=settings.context_recent_turns,
    keep_recent_turns=settings.context_keep_recent_turns,
)
chat_application = ContextAwareChatApplicationService(
    agent_runner=AgnoAgentRunner(agent_provider=lambda: insurance_agent),
    context_management=context_management,
    session_lock=PostgresSessionTurnLock(settings.context_session_lock_timeout_seconds),
)

base_app = FastAPI(title="Bolee Insurance Agent Service", version="0.1.0")
agentos_route_policy = AgentOSRouteScopePolicy(
    chat_scope=settings.oauth_chat_scope,
    operator_scope=settings.oauth_operator_scope,
    tag_generation_scope=settings.oauth_tag_generation_scope,
)
base_app.add_middleware(
    AgentOSOAuthScopeMiddleware,
    token_validator=oauth_jwt_validator,
    route_policy=agentos_route_policy,
    admin_issuer=settings.oauth_agentos_admin_issuer,
    admin_audience=settings.oauth_agentos_admin_audience,
)
# Added after auth so Starlette places CORS outside the authorization middleware;
# allowed browser preflights terminate here without bypassing authorization on requests.
base_app.add_middleware(
    CORSMiddleware,
    allow_origins=list(settings.agentos_admin_cors_origins),
    allow_credentials=False,
    allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
    max_age=600,
)


@base_app.post("/internal/chat", response_model=ChatResponse)
def chat(
    request: ChatRequest,
):
    if not settings.model_api_key:
        raise HTTPException(status_code=503, detail="model API credentials are not configured")

    tool_result_collector = AgentToolResultCollector(
        context_result_token_limit=settings.context_tool_result_reserve_tokens
    )
    dependencies = AgentDependencies(
        java_internal_api=java_internal_api,
        authorization_context=request.authorization_context,
        tool_result_collector=tool_result_collector,
        selected_policy_id=request.selected_policy_id,
    )

    try:
        result = chat_application.run(
            request.message,
            user_id=request.user_id,
            session_id=request.session_id,
            dependencies=dependencies.as_agno_dependencies(),
            tool_result_collector=tool_result_collector,
        )
    except ContextInputTooLargeError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except ContextCompactionUnavailableError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except SessionTurnInProgressError as exc:
        raise HTTPException(status_code=409, detail=str(exc)) from exc
    except Exception as exc:
        logger.exception("Agno run failed for session %s", request.session_id)
        raise HTTPException(status_code=502, detail="agent model request failed") from exc

    return build_chat_response(request, result, tool_result_collector)


@base_app.post("/internal/chat/stream")
def stream_chat(request: ChatRequest):
    """Stream answer text deltas, followed by the same final contract as /internal/chat."""
    if not settings.model_api_key:
        raise HTTPException(status_code=503, detail="model API credentials are not configured")

    def events():
        tool_result_collector = AgentToolResultCollector(
            context_result_token_limit=settings.context_tool_result_reserve_tokens
        )
        dependencies = AgentDependencies(
            java_internal_api=java_internal_api,
            authorization_context=request.authorization_context,
            tool_result_collector=tool_result_collector,
            selected_policy_id=request.selected_policy_id,
        )
        final_output = None
        try:
            stream = chat_application.stream(
                request.message,
                user_id=request.user_id,
                session_id=request.session_id,
                dependencies=dependencies.as_agno_dependencies(),
                tool_result_collector=tool_result_collector,
            )

            for item in stream:
                if chat_application.is_final_output(item):
                    final_output = item
                    continue
                event_name = getattr(item, "event", "")
                event_name = getattr(
                    event_name, "value", getattr(event_name, "name", str(event_name))
                )
                content = getattr(item, "content", None)
                if (
                    "runcontent" in str(event_name).replace("_", "").lower()
                    and isinstance(content, str)
                    and content
                ):
                    yield _sse("delta", {"text": content})
            if final_output is None:
                raise RuntimeError("Agent stream ended without final run output")
            response = build_chat_response(request, final_output, tool_result_collector)
            yield _sse("complete", response.model_dump(mode="json", by_alias=True))
        except ContextInputTooLargeError:
            yield _sse("error", {"message": "当前对话上下文较长，请缩短本轮问题后重试。"})
        except ContextCompactionUnavailableError as exc:
            yield _sse("error", {"message": str(exc)})
        except SessionTurnInProgressError:
            yield _sse("error", {"message": "当前会话有请求正在处理，请稍后重试。"})
        except Exception:
            logger.exception("Agno streaming run failed for session %s", request.session_id)
            yield _sse("error", {"message": "回复生成失败，请稍后重试。"})

    return StreamingResponse(
        events(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache, no-store", "X-Accel-Buffering": "no"},
    )


def build_chat_response(
    request: ChatRequest,
    result,
    tool_result_collector: AgentToolResultCollector,
) -> ChatResponse:
    terms_search = tool_result_collector.terms_search
    answer = terms_search.answer if terms_search is not None else result.content
    if not isinstance(answer, str):
        answer = str(answer or "")
    answer = answer.strip()
    if not answer:
        raise HTTPException(status_code=502, detail="Agent returned an empty answer")
    if len(answer) > 12000:
        raise HTTPException(status_code=502, detail="Agent answer exceeded the response limit")

    recommendation_result = (
        None if terms_search is not None else tool_result_collector.product_recommendation
    )
    comparison_result = (
        None if terms_search is not None else tool_result_collector.product_comparison
    )
    own_policy_summary = (
        None if terms_search is not None else tool_result_collector.own_policy_summary
    )
    own_policy_detail = (
        None if terms_search is not None else tool_result_collector.own_policy_detail
    )
    if recommendation_result is not None:
        recommendations = recommendation_result.candidates
        recommendation_status = recommendation_result.status
        evaluation_completeness = recommendation_result.evaluation_completeness
        missing_criteria = recommendation_result.missing_criteria
        unsupported_criteria = recommendation_result.unsupported_criteria
        unresolved_criteria = recommendation_result.unresolved_criteria
        excluded_product_count = recommendation_result.excluded_product_count
        catalog_version = recommendation_result.catalog_version
        rule_version = recommendation_result.rule_version
    else:
        search_result = (
            None if terms_search is not None else tool_result_collector.product_catalog_search
        )
        recommendations = (
            search_result.candidates
            if search_result is not None and search_result.status == RecommendationStatus.MATCHED
            else []
        )
        recommendation_status = None
        evaluation_completeness = None
        missing_criteria = []
        unsupported_criteria = []
        unresolved_criteria = []
        excluded_product_count = 0
        catalog_version = None
        rule_version = None
    return ChatResponse(
        session_id=request.session_id,
        run_id=str(result.run_id),
        answer=answer,
        term_citations=terms_search.term_citations if terms_search is not None else [],
        recommendations=recommendations,
        product_comparison=comparison_result,
        own_policy_summary=own_policy_summary,
        own_policy_detail=own_policy_detail,
        recommendation_status=recommendation_status,
        evaluation_completeness=evaluation_completeness,
        missing_criteria=missing_criteria,
        unsupported_criteria=unsupported_criteria,
        unresolved_criteria=unresolved_criteria,
        excluded_product_count=excluded_product_count,
        catalog_version=catalog_version,
        rule_version=rule_version,
    )


def _sse(event: str, payload: dict) -> str:
    import json

    data = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    return f"event: {event}\ndata: {data}\n\n"


@base_app.get(
    "/internal/sessions/{session_id}/messages",
    response_model=AgentHistoryPage,
)
def get_session_messages(
    session_id: str,
    response: Response,
    user_id: str = Header(alias="X-Agno-User-Id", min_length=1, max_length=64),
    before_run_index: int | None = Query(default=None, ge=1),
    limit: int = Query(default=25, ge=1, le=50),
) -> AgentHistoryPage:
    """Read only plain user/assistant messages for a Java owner-checked session."""
    response.headers["Cache-Control"] = "no-store, private"
    response.headers["Pragma"] = "no-cache"
    if re.fullmatch(r"[A-Za-z0-9_-]{1,64}", session_id) is None:
        raise HTTPException(status_code=400, detail="invalid session id")
    try:
        return get_session_history(session_id, user_id, before_run_index, limit)
    except Exception as exc:
        logger.exception("Agno session history read failed")
        raise HTTPException(status_code=503, detail="session history unavailable") from exc


@base_app.post(
    "/internal/sessions/previews",
    response_model=AgentSessionPreviewsResponse,
)
def get_session_previews_endpoint(
    request: AgentSessionPreviewsRequest,
    user_id: str = Header(alias="X-Agno-User-Id", min_length=1, max_length=64),
) -> AgentSessionPreviewsResponse:
    """Return short titles for Java-validated sessions owned by the authenticated user."""
    if any(
        re.fullmatch(r"[A-Za-z0-9_-]{1,64}", session_id) is None
        for session_id in request.session_ids
    ):
        raise HTTPException(status_code=400, detail="invalid session id")
    try:
        return get_session_previews(request.session_ids, user_id)
    except Exception as exc:
        logger.exception("Agno session preview read failed")
        raise HTTPException(status_code=503, detail="session previews unavailable") from exc


@base_app.post(
    "/internal/product-tags/generate",
    response_model=ProductSearchTagGenerationOutput,
)
def generate_product_tags(request: ProductSearchTagInput):
    """Generate grounded tag suggestions for an authenticated operator workflow."""
    try:
        return generate_product_search_tags(request)
    except ProductSearchTagGenerationError as exc:
        status_code = 503 if "credentials are not configured" in str(exc) else 502
        raise HTTPException(status_code=status_code, detail=str(exc)) from exc


@base_app.delete("/internal/sessions/{session_id}", status_code=204)
def delete_session(session_id: str) -> Response:
    """Delete a Java-authorized session and its associated Agno traces."""
    if re.fullmatch(r"[A-Za-z0-9_-]{1,64}", session_id) is None:
        raise HTTPException(status_code=400, detail="invalid session id")
    try:
        delete_session_and_traces(session_id)
    except Exception as exc:
        logger.exception("Agno session cleanup failed")
        raise HTTPException(status_code=503, detail="session cleanup failed") from exc
    return Response(status_code=204)


@base_app.post("/internal/retention/traces")
def cleanup_expired_traces(retention_days: int = Query(gt=0, le=3650)) -> dict[str, int]:
    """Delete trace data past the Java-approved retention window."""
    try:
        deleted = delete_expired_traces(retention_days)
    except Exception as exc:
        logger.exception("Agno trace retention cleanup failed")
        raise HTTPException(status_code=503, detail="trace cleanup failed") from exc
    return {"deleted_rows": deleted}


@base_app.post("/internal/retention/sessions")
def cleanup_expired_sessions(retention_days: int = Query(gt=0, le=3650)) -> dict[str, int]:
    """Remove old Agno sessions/runs, including sessions created before Java tracking."""
    try:
        deleted = delete_expired_sessions(retention_days)
    except Exception as exc:
        logger.exception("Agno session retention cleanup failed")
        raise HTTPException(status_code=503, detail="session cleanup failed") from exc
    return {"deleted_sessions": deleted}


agent_os = AgentOS(
    id="bolee-insurance-agent-os",
    db=get_postgres_db(),
    tracing=True,
    # The outer ASGI policy authenticates Java service tokens and enforces
    # management roles from short-lived, audience-bound Java admin tokens.
    # Customer data authorization remains enforced by Java tool endpoints.
    authorization=False,
    agents=[insurance_agent],
    base_app=base_app,
)
app = agent_os.get_app()
agentos_route_policy.validate_openapi(app.openapi())

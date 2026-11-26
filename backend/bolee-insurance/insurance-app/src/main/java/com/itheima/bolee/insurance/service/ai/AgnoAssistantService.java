package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.framework.commons.dto.security.UserVO;
import com.itheima.bolee.framework.commons.enums.basic.BaseEnum;
import com.itheima.bolee.framework.commons.enums.security.AuthEnum;
import com.itheima.bolee.framework.commons.exception.ProjectException;
import com.itheima.bolee.insurance.dto.ai.AgnoChatRequest;
import com.itheima.bolee.insurance.dto.ai.AgnoChatResponse;
import com.itheima.bolee.insurance.dto.ai.AiAssistantChatRequestVO;
import com.itheima.bolee.insurance.dto.ai.AiAssistantChatResponseVO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyDetailResponseVO;
import com.itheima.bolee.insurance.dto.ai.contract.AgentSessionPreviewDTO;
import com.itheima.bolee.insurance.dto.ai.contract.AgentSessionPreviewsRequestDTO;
import com.itheima.bolee.insurance.dto.ai.contract.AgentSessionPreviewsResponseDTO;
import com.itheima.bolee.insurance.dto.ai.contract.AgentHistoryMessageDTO;
import com.itheima.bolee.insurance.dto.ai.contract.AgentHistoryPageDTO;
import com.itheima.bolee.insurance.dto.ai.contract.AiConversationListItemDTO;
import com.itheima.bolee.insurance.dto.ai.contract.AiConversationListResponseDTO;
import com.itheima.bolee.insurance.dto.ai.contract.AiConversationMessagesResponseDTO;
import com.itheima.bolee.insurance.dto.ai.contract.AiMessageDTO;
import com.itheima.bolee.insurance.dto.ai.contract.AiMessageRole;
import com.itheima.bolee.insurance.dto.ai.contract.AiMessageStructuredDataDTO;
import com.itheima.bolee.insurance.pojo.ai.AiConversationDO;
import com.itheima.bolee.insurance.pojo.ai.AiTurnDO;
import com.itheima.bolee.insurance.pojo.ai.AiTurnStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Calls the private Agno service after resolving identity in the Java application. */
@Slf4j
@Service
public class AgnoAssistantService {

    private static final int MAX_MESSAGE_LENGTH = 4000;
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final boolean enabled;
    private final String serviceUrl;
    private final AgnoOAuthClientCredentials oauthClientCredentials;
    private final AgnoAuthorizationContextService authorizationContextService;
    private final ObjectProvider<AiConversationService> conversationServiceProvider;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiResponseCipher responseCipher;
    @Autowired
    private AiOwnPolicyQueryService ownPolicyQueryService;

    /**
     * Spring 创建本服务时调用一次：读取配置、接收其他 Spring 服务，并准备访问 Python Agent 的 HTTP 客户端。
     * 这里不处理用户消息，也不做意图识别；用户消息由下面的 chat/streamChat 方法处理。
     */
    public AgnoAssistantService(
            // 是否启用 Agent 客服功能。
            @Value("${agno.enabled:false}") boolean enabled,
            // Python Agent 服务的地址，例如 http://agno-agent:8000。
            @Value("${agno.service-url:http://127.0.0.1:8000}") String serviceUrl,
            // 建立到 Python 服务的连接时，最多等待多久。
            @Value("${agno.connect-timeout-ms:3000}") int connectTimeoutMs,
            // 等待 Python 服务返回数据时，最多等待多久。
            @Value("${agno.read-timeout-ms:120000}") int readTimeoutMs,
            // 是否启用了 AI 对话持久化；Agent 启用时也必须开启。
            @Value("${ai.persistence.enabled:false}") boolean aiPersistenceEnabled,
            // 用于加密保存 AI 响应幂等记录的密钥。
            @Value("${ai.idempotency.encryption-key:}") String responseEncryptionKey,
            // 生成并校验传给 Python Agent 的用户授权上下文。
            AgnoAuthorizationContextService authorizationContextService,
            // 负责向 Python Agent 请求 OAuth 服务凭证。
            AgnoOAuthClientCredentials oauthClientCredentials,
            // 按需获取对话持久化服务；该服务可能因配置未启用而不存在。
            ObjectProvider<AiConversationService> conversationServiceProvider,
            // Spring 提供的 JSON 序列化/反序列化组件。
            ObjectMapper objectMapper) {
        this.enabled = enabled;

        // 清理服务地址末尾的斜杠，避免后续拼接接口路径时出现双斜杠。
        String normalizedServiceUrl = serviceUrl == null ? "" : serviceUrl.trim().replaceAll("/+$", "");
        URI serviceUri;
        try {
            serviceUri = URI.create(normalizedServiceUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("agno.service-url must be a valid HTTP(S) URL", exception);
        }
        // 启动时就检查服务地址，避免配置错误直到用户发起聊天才暴露。
        if (!("http".equalsIgnoreCase(serviceUri.getScheme())
                || "https".equalsIgnoreCase(serviceUri.getScheme()))
                || !StringUtils.hasText(serviceUri.getHost())) {
            throw new IllegalArgumentException("agno.service-url must be a valid HTTP(S) URL");
        }
        // 连接超时和读取超时必须为正数。
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("Agno HTTP timeouts must be greater than zero");
        }

        // 保存经过校验的配置和 Spring 注入的协作者，供后续聊天请求使用。
        this.serviceUrl = normalizedServiceUrl;
        this.authorizationContextService = authorizationContextService;
        this.oauthClientCredentials = oauthClientCredentials;
        this.conversationServiceProvider = conversationServiceProvider;
        this.objectMapper = objectMapper;

        // Agent 会保存会话数据，因此启用客服功能时要求同时配置持久化和数据保留策略。
        if (enabled && !aiPersistenceEnabled) {
            throw new IllegalStateException(
                    "Agno customer chat requires AI conversation persistence and retention configuration");
        }

        // 仅在功能启用时创建响应加密器；功能关闭时不需要加密密钥。
        this.responseCipher = enabled ? new AiResponseCipher(responseEncryptionKey) : null;

        // 创建后续用于调用 Python Agent 的 HTTP 客户端，并设置连接和读取超时。
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public AiAssistantChatResponseVO chat(AiAssistantChatRequestVO request, UserVO currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new ProjectException(AuthEnum.NEED_LOGIN);
        }
        if (!enabled) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if (request == null || !StringUtils.hasText(request.getMessage())
                || request.getMessage().length() > MAX_MESSAGE_LENGTH) {
            throw new ProjectException(BaseEnum.VALID_EXCEPTION);
        }

        String clientSessionId = request.getSessionId();
        if (!StringUtils.hasText(clientSessionId)) {
            clientSessionId = UUID.randomUUID().toString();
        } else if (!SESSION_ID_PATTERN.matcher(clientSessionId).matches()) {
            throw new ProjectException(BaseEnum.VALID_EXCEPTION);
        }

        String userId = String.valueOf(currentUser.getId());
        validateSelectedPolicy(request.getSelectedPolicyId(), userId);
        String agnoSessionId = UUID.nameUUIDFromBytes(
                (userId + ":" + clientSessionId).getBytes(StandardCharsets.UTF_8)).toString();
        AiConversationService conversationService = conversationServiceProvider.getIfAvailable();
        AiTurnDO turn = null;
        String clientMessageId = request.getClientMessageId();
        if (conversationService == null || !isUuid(clientMessageId)) {
            throw new ProjectException(BaseEnum.VALID_EXCEPTION);
        }
        {
            String traceId = UUID.randomUUID().toString();
            AiTurnStartResult start = conversationService.startTurn(
                    currentUser.getId(), clientSessionId, clientMessageId, traceId,
                    request.getMessage().trim(), request.getSelectedPolicyId());
            if (start.getOutcome() == AiTurnStartResult.Outcome.IDEMPOTENCY_CONFLICT
                    || start.getOutcome() == AiTurnStartResult.Outcome.CONVERSATION_UNAVAILABLE) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            turn = start.getTurn();
            if (start.getOutcome() == AiTurnStartResult.Outcome.EXISTING) {
                if (turn.getStatus() == AiTurnStatus.PROCESSING) {
                    return processingResponse(clientSessionId, clientMessageId, turn.getTurnId());
                }
                if (turn.getStatus() == AiTurnStatus.COMPLETED) {
                    return replayResponse(turn, currentUser.getId());
                }
                if (turn.getStatus() == AiTurnStatus.FAILED) {
                    return failedResponse(clientSessionId, clientMessageId, turn.getTurnId());
                }
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            AiConversationDO conversation = conversationService.getOrCreateConversation(
                    currentUser.getId(), clientSessionId);
            agnoSessionId = conversation.getAgnoSessionId();
        }
        try {
            String authorizationContext = authorizationContextService.issue(userId, agnoSessionId);
            AgnoChatRequest agnoRequest = new AgnoChatRequest(
                    userId, agnoSessionId, authorizationContext, request.getSelectedPolicyId(), request.getMessage().trim());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(oauthClientCredentials.getAccessToken());
            HttpEntity<AgnoChatRequest> entity = new HttpEntity<>(agnoRequest, headers);
            AgnoChatResponse response = restTemplate.exchange(
                    serviceUrl + "/internal/chat",
                    HttpMethod.POST,
                    entity,
                    AgnoChatResponse.class).getBody();
            if (response == null || !StringUtils.hasText(response.getAnswer())) {
                throw new RestClientException("Agno returned an empty response");
            }
            AiAssistantChatResponseVO customerResponse = AgnoChatResponseMapper
                    .toCustomerResponse(clientSessionId, response);
            customerResponse.setRequestStatus("COMPLETED");
            customerResponse.setClientMessageId(clientMessageId);
            customerResponse.setTurnId(turn.getTurnId());
            String encryptedResponse = responseCipher.encrypt(serialize(customerResponse),
                    responseAad(currentUser.getId(), turn.getTurnId()));
            if (!conversationService.completeTurn(currentUser.getId(), turn.getTurnId(),
                    response.getRunId(), encryptedResponse)) {
                throw new RestClientException("Could not complete persisted AI turn");
            }
            return customerResponse;
        } catch (RestClientException exception) {
            if (turn != null) conversationService.failTurn(currentUser.getId(), turn.getTurnId(), "AGENT_REQUEST_FAILED");
            log.warn("Agno assistant request failed");
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        } catch (IllegalStateException exception) {
            if (turn != null) conversationService.failTurn(currentUser.getId(), turn.getTurnId(), "AGENT_AUTH_FAILED");
            log.warn("Agno OAuth authentication failed");
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        } catch (RuntimeException exception) {
            if (turn != null) conversationService.failTurn(currentUser.getId(), turn.getTurnId(), "AGENT_REQUEST_FAILED");
            log.warn("Agno assistant request failed before completion");
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
    }

    /** Streams answer deltas to the customer while persisting only the completed, encrypted response. */
    public void streamChat(AiAssistantChatRequestVO request, UserVO currentUser, OutputStream output) {
        if (currentUser == null || currentUser.getId() == null) throw new ProjectException(AuthEnum.NEED_LOGIN);
        if (!enabled || request == null || !StringUtils.hasText(request.getMessage())
                || request.getMessage().length() > MAX_MESSAGE_LENGTH || !isUuid(request.getClientMessageId())) {
            throw new ProjectException(BaseEnum.VALID_EXCEPTION);
        }
        String clientSessionId = request.getSessionId();
        if (!StringUtils.hasText(clientSessionId)) clientSessionId = UUID.randomUUID().toString();
        else if (!SESSION_ID_PATTERN.matcher(clientSessionId).matches()) throw new ProjectException(BaseEnum.VALID_EXCEPTION);
        final String resolvedSessionId = clientSessionId;

        AiConversationService conversationService = conversationServiceProvider.getIfAvailable();
        if (conversationService == null) throw new ProjectException(BaseEnum.REMOTE_FAIL);
        String userId = String.valueOf(currentUser.getId());
        validateSelectedPolicy(request.getSelectedPolicyId(), userId);
        AiTurnStartResult start = conversationService.startTurn(currentUser.getId(), resolvedSessionId,
                request.getClientMessageId(), UUID.randomUUID().toString(), request.getMessage().trim(),
                request.getSelectedPolicyId());
        if (start.getOutcome() == AiTurnStartResult.Outcome.IDEMPOTENCY_CONFLICT
                || start.getOutcome() == AiTurnStartResult.Outcome.CONVERSATION_UNAVAILABLE) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        AiTurnDO turn = start.getTurn();
        if (start.getOutcome() == AiTurnStartResult.Outcome.EXISTING) {
            if (turn.getStatus() == AiTurnStatus.PROCESSING) {
                writeSse(output, "processing", processingResponse(resolvedSessionId,
                        request.getClientMessageId(), turn.getTurnId()));
                return;
            }
            if (turn.getStatus() == AiTurnStatus.COMPLETED) {
                writeSse(output, "complete", replayResponse(turn, currentUser.getId()));
                return;
            }
            if (turn.getStatus() == AiTurnStatus.FAILED) {
                writeSse(output, "error", failedResponse(resolvedSessionId,
                        request.getClientMessageId(), turn.getTurnId()));
                return;
            }
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        AiConversationDO conversation = conversationService.getOrCreateConversation(currentUser.getId(), resolvedSessionId);
        String agnoSessionId = conversation.getAgnoSessionId();
        try {
            String authorizationContext = authorizationContextService.issue(userId, agnoSessionId);
            AgnoChatRequest agnoRequest = new AgnoChatRequest(userId, agnoSessionId, authorizationContext,
                    request.getSelectedPolicyId(), request.getMessage().trim());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(oauthClientCredentials.getAccessToken());
            RequestCallback callback = httpRequest -> {
                httpRequest.getHeaders().putAll(headers);
                objectMapper.writeValue(httpRequest.getBody(), agnoRequest);
            };
            AiAssistantChatResponseVO result = restTemplate.execute(
                    serviceUrl + "/internal/chat/stream", HttpMethod.POST, callback,
                    new ResponseExtractor<AiAssistantChatResponseVO>() {
                        @Override
                        public AiAssistantChatResponseVO extractData(ClientHttpResponse response) throws IOException {
                            if (!response.getStatusCode().is2xxSuccessful()) {
                                throw new IOException("Agno streaming endpoint returned a failure status");
                            }
                            BufferedReader reader = new BufferedReader(new InputStreamReader(
                                    response.getBody(), StandardCharsets.UTF_8));
                            String eventName = "message";
                            StringBuilder data = new StringBuilder();
                            AiAssistantChatResponseVO completed = null;
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("event:")) eventName = line.substring(6).trim();
                                else if (line.startsWith("data:")) {
                                    if (data.length() > 0) data.append('\n');
                                    data.append(line.substring(5).trim());
                                } else if (line.isEmpty()) {
                                    if ("delta".equals(eventName)) {
                                        writeRawSse(output, "delta", data.toString());
                                    } else if ("complete".equals(eventName)) {
                                        AgnoChatResponse agentResponse = objectMapper.readValue(data.toString(), AgnoChatResponse.class);
                                        if (!StringUtils.hasText(agentResponse.getAnswer())) throw new IOException("Agno returned an empty answer");
                                        AiAssistantChatResponseVO customerResponse = AgnoChatResponseMapper
                                                .toCustomerResponse(resolvedSessionId, agentResponse);
                                        customerResponse.setRequestStatus("COMPLETED");
                                        customerResponse.setClientMessageId(request.getClientMessageId());
                                        customerResponse.setTurnId(turn.getTurnId());
                                        String encrypted = responseCipher.encrypt(serialize(customerResponse),
                                                responseAad(currentUser.getId(), turn.getTurnId()));
                                        if (!conversationService.completeTurn(currentUser.getId(), turn.getTurnId(),
                                                agentResponse.getRunId(), encrypted)) throw new IOException("Could not persist completed AI turn");
                                        completed = customerResponse;
                                    } else if ("error".equals(eventName)) {
                                        throw new IOException("Agno streaming request failed");
                                    }
                                    eventName = "message";
                                    data.setLength(0);
                                }
                            }
                            if (completed == null) throw new IOException("Agno stream ended without a completed response");
                            writeSse(output, "complete", completed);
                            return completed;
                        }
                    });
            if (result == null) throw new RestClientException("Agno returned an empty stream");
        } catch (Exception exception) {
            conversationService.failTurn(currentUser.getId(), turn.getTurnId(), "AGENT_REQUEST_FAILED");
            Throwable rootCause = exception;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            // Keep diagnostics useful without logging customer text, credentials, or response bodies.
            StackTraceElement origin = exception.getStackTrace().length == 0
                    ? null : exception.getStackTrace()[0];
            log.warn("Agno assistant streaming request failed (type={}, rootType={}, origin={})",
                    exception.getClass().getSimpleName(), rootCause.getClass().getSimpleName(),
                    origin == null ? "unknown" : origin.getClassName() + "." + origin.getMethodName());
            Map<String, Object> failure = new HashMap<String, Object>();
            failure.put("message", "这次回复没有完成，请重新发起问题。");
            failure.put("requestStatus", "FAILED");
            failure.put("clientMessageId", request.getClientMessageId());
            failure.put("turnId", turn.getTurnId());
            writeSse(output, "error", failure);
        }
    }

    private void writeSse(OutputStream output, String event, Object payload) {
        try { writeRawSse(output, event, objectMapper.writeValueAsString(payload)); }
        catch (Exception ignored) { /* A disconnected browser must not prevent response persistence. */ }
    }

    private void writeRawSse(OutputStream output, String event, String json) {
        try {
            output.write(("event: " + event + "\ndata: " + json + "\n\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (IOException ignored) { /* Continue reading the upstream so its final answer can be persisted. */ }
    }

    private AiAssistantChatResponseVO replayResponse(AiTurnDO turn, Long ownerUserId) {
        try {
            return objectMapper.readValue(responseCipher.decrypt(turn.getEncryptedResponse(),
                            responseAad(ownerUserId, turn.getTurnId())),
                    AiAssistantChatResponseVO.class);
        } catch (Exception exception) {
            log.error("Could not replay persisted AI response for turn {}", turn.getTurnId());
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
    }

    /** Returns an owner-scoped, cursor-paged list of live conversations. */
    public AiConversationListResponseDTO listConversations(String cursor, int limit, UserVO currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new ProjectException(AuthEnum.NEED_LOGIN);
        }
        if (!enabled || limit < 1 || limit > 50) throw new ProjectException(BaseEnum.VALID_EXCEPTION);
        ConversationCursor decodedCursor = parseConversationCursor(cursor);
        AiConversationService conversationService = conversationServiceProvider.getIfAvailable();
        if (conversationService == null) throw new ProjectException(BaseEnum.REMOTE_FAIL);

        List<AiConversationDO> page = conversationService.findReadablePage(currentUser.getId(),
                decodedCursor == null ? null : decodedCursor.time,
                decodedCursor == null ? null : decodedCursor.id, limit + 1);
        boolean hasMore = page.size() > limit;
        if (hasMore) page = new ArrayList<AiConversationDO>(page.subList(0, limit));
        AiConversationListResponseDTO result = new AiConversationListResponseDTO();
        result.setHasMore(hasMore);
        if (page.isEmpty()) return result;

        Map<String, AgentSessionPreviewDTO> previews = readSessionPreviews(page, currentUser.getId());
        List<AiConversationListItemDTO> items = new ArrayList<AiConversationListItemDTO>();
        for (AiConversationDO conversation : page) {
            if (conversationService.findReadableConversation(currentUser.getId(), conversation.getSessionId()) == null) {
                continue;
            }
            AgentSessionPreviewDTO preview = previews.get(conversation.getAgnoSessionId());
            AiConversationListItemDTO item = new AiConversationListItemDTO();
            item.setSessionId(conversation.getSessionId());
            item.setTitle(preview != null && StringUtils.hasText(preview.getTitle())
                    ? preview.getTitle() : "保险咨询");
            item.setPreview(preview != null && StringUtils.hasText(preview.getPreview())
                    ? preview.getPreview() : "点击继续此对话");
            item.setCreatedAt(toInstant(conversation.getCreateTime()));
            item.setLastMessageAt(toInstant(conversation.getLastMessageAt() == null
                    ? conversation.getUpdateTime() : conversation.getLastMessageAt()));
            items.add(item);
        }
        result.setConversations(items);
        if (hasMore && !page.isEmpty()) {
            AiConversationDO last = page.get(page.size() - 1);
            LocalDateTime activity = last.getLastMessageAt() == null ? last.getCreateTime() : last.getLastMessageAt();
            result.setNextCursor(encodeConversationCursor(activity, last.getId()));
        }
        return result;
    }

    private Map<String, AgentSessionPreviewDTO> readSessionPreviews(List<AiConversationDO> conversations,
                                                                     Long ownerUserId) {
        Map<String, AgentSessionPreviewDTO> result = new HashMap<String, AgentSessionPreviewDTO>();
        try {
            List<String> agnoSessionIds = new ArrayList<String>();
            for (AiConversationDO conversation : conversations) agnoSessionIds.add(conversation.getAgnoSessionId());
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(oauthClientCredentials.getAccessToken());
            headers.set("X-Agno-User-Id", String.valueOf(ownerUserId));
            headers.setContentType(MediaType.APPLICATION_JSON);
            AgentSessionPreviewsResponseDTO response = restTemplate.exchange(
                    serviceUrl + "/internal/sessions/previews", HttpMethod.POST,
                    new HttpEntity<AgentSessionPreviewsRequestDTO>(
                            new AgentSessionPreviewsRequestDTO(agnoSessionIds), headers),
                    AgentSessionPreviewsResponseDTO.class).getBody();
            if (response != null && response.getSessions() != null) {
                for (AgentSessionPreviewDTO preview : response.getSessions()) {
                    if (preview != null && StringUtils.hasText(preview.getSessionId())) {
                        result.put(preview.getSessionId(), preview);
                    }
                }
            }
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Agno conversation preview request failed; returning metadata-only titles");
        }
        return result;
    }

    private static String encodeConversationCursor(LocalDateTime time, Long id) {
        String value = time.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ConversationCursor parseConversationCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) return null;
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid conversation cursor");
            LocalDateTime time = LocalDateTime.parse(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (id <= 0) throw new IllegalArgumentException("Invalid conversation cursor");
            return new ConversationCursor(time, id);
        } catch (RuntimeException exception) {
            throw new ProjectException(BaseEnum.VALID_EXCEPTION);
        }
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static final class ConversationCursor {
        private final LocalDateTime time;
        private final Long id;

        private ConversationCursor(LocalDateTime time, Long id) {
            this.time = time;
            this.id = id;
        }
    }

    /** Reads paged customer-visible messages only after resolving Java session ownership. */
    public AiConversationMessagesResponseDTO getConversationMessages(
            String clientSessionId, String cursor, int limit, UserVO currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new ProjectException(AuthEnum.NEED_LOGIN);
        }
        if (!enabled || !StringUtils.hasText(clientSessionId)
                || !SESSION_ID_PATTERN.matcher(clientSessionId).matches()
                || limit < 1 || limit > 50) {
            throw new ProjectException(BaseEnum.VALID_EXCEPTION);
        }
        Long beforeRunIndex = null;
        if (StringUtils.hasText(cursor)) {
            try {
                beforeRunIndex = Long.parseLong(cursor);
                if (beforeRunIndex <= 0) throw new NumberFormatException("cursor must be positive");
            } catch (NumberFormatException exception) {
                throw new ProjectException(BaseEnum.VALID_EXCEPTION);
            }
        }

        AiConversationService conversationService = conversationServiceProvider.getIfAvailable();
        if (conversationService == null) throw new ProjectException(BaseEnum.REMOTE_FAIL);
        AiConversationDO conversation = conversationService.findReadableConversation(
                currentUser.getId(), clientSessionId);
        AiConversationMessagesResponseDTO customerPage = new AiConversationMessagesResponseDTO();
        customerPage.setSessionId(clientSessionId);
        if (conversation == null) return customerPage;

        try {
            UriComponentsBuilder url = UriComponentsBuilder.fromHttpUrl(serviceUrl)
                    .pathSegment("internal", "sessions", conversation.getAgnoSessionId(), "messages")
                    .queryParam("limit", limit);
            if (beforeRunIndex != null) url.queryParam("before_run_index", beforeRunIndex);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(oauthClientCredentials.getAccessToken());
            headers.set("X-Agno-User-Id", String.valueOf(currentUser.getId()));
            AgentHistoryPageDTO agentPage = restTemplate.exchange(
                    url.build().encode().toUri(), HttpMethod.GET, new HttpEntity<Void>(headers),
                    AgentHistoryPageDTO.class).getBody();
            if (agentPage == null) throw new RestClientException("Agno returned an empty history page");

            // A delete or expiry may race with the Python read. Re-check before release so a
            // session marked DELETE_PENDING is immediately hidden from further customer reads.
            if (conversationService.findReadableConversation(currentUser.getId(), clientSessionId) == null) {
                return customerPage;
            }

            List<AgentHistoryMessageDTO> sourceMessages = agentPage.getMessages();
            List<String> runIds = new ArrayList<String>();
            LinkedHashSet<String> uniqueRunIds = new LinkedHashSet<String>();
            if (sourceMessages != null) {
                for (AgentHistoryMessageDTO message : sourceMessages) {
                    if (message != null && StringUtils.hasText(message.getRunId())) {
                        uniqueRunIds.add(message.getRunId());
                    }
                }
            }
            runIds.addAll(uniqueRunIds);
            Map<String, AiTurnDO> turnsByRunId = new HashMap<String, AiTurnDO>();
            for (AiTurnDO turn : conversationService.findCompletedTurnsByAgnoRunIds(
                    currentUser.getId(), conversation.getId(), runIds)) {
                if (StringUtils.hasText(turn.getAgnoRunId()) && StringUtils.hasText(turn.getTurnId())) {
                    turnsByRunId.put(turn.getAgnoRunId(), turn);
                }
            }
            List<AiMessageDTO> messages = new ArrayList<AiMessageDTO>();
            if (sourceMessages != null) {
                for (AgentHistoryMessageDTO source : sourceMessages) {
                    if (source == null || source.getRole() == null || !StringUtils.hasText(source.getMessageId())
                            || !StringUtils.hasText(source.getContent()) || source.getContent().length() > 12000) {
                        continue;
                    }
                    if (source.getRole() != AiMessageRole.USER && source.getRole() != AiMessageRole.ASSISTANT) {
                        continue;
                    }
                    AiMessageDTO message = new AiMessageDTO();
                    message.setMessageId(source.getMessageId());
                    AiTurnDO turn = turnsByRunId.get(source.getRunId());
                    message.setTurnId(turn == null ? null : turn.getTurnId());
                    message.setRole(source.getRole());
                    message.setContent(source.getContent());
                    message.setCreatedAt(source.getCreatedAt());
                    if (source.getRole() == AiMessageRole.ASSISTANT && turn != null) {
                        message.setStructuredData(readStructuredData(turn, currentUser.getId()));
                    }
                    messages.add(message);
                }
            }
            if (conversationService.findReadableConversation(currentUser.getId(), clientSessionId) == null) {
                return customerPage;
            }
            customerPage.setMessages(messages);
            customerPage.setNextCursor(agentPage.getNextCursor());
            customerPage.setHasMore(Boolean.TRUE.equals(agentPage.getHasMore()));
            return customerPage;
        } catch (RestClientException exception) {
            log.warn("Agno conversation history request failed");
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        } catch (IllegalStateException exception) {
            log.warn("Agno OAuth authentication failed for conversation history");
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
    }

    private AiMessageStructuredDataDTO readStructuredData(AiTurnDO turn, Long ownerUserId) {
        try {
            String json = responseCipher.decrypt(turn.getEncryptedResponse(),
                    responseAad(ownerUserId, turn.getTurnId()));
            AiAssistantChatResponseVO response = objectMapper.readValue(json, AiAssistantChatResponseVO.class);
            AiMessageStructuredDataDTO structured = new AiMessageStructuredDataDTO();
            structured.setRecommendations(response.getRecommendations());
            structured.setTermCitations(response.getTermCitations());
            structured.setProductComparison(response.getProductComparison());
            structured.setOwnPolicySummary(response.getOwnPolicySummary());
            structured.setOwnPolicyDetail(response.getOwnPolicyDetail());
            structured.setRecommendationStatus(response.getRecommendationStatus());
            structured.setEvaluationCompleteness(response.getEvaluationCompleteness());
            structured.setMissingCriteria(response.getMissingCriteria());
            structured.setUnsupportedCriteria(response.getUnsupportedCriteria());
            structured.setUnresolvedCriteria(response.getUnresolvedCriteria());
            structured.setExcludedProductCount(response.getExcludedProductCount());
            structured.setCatalogVersion(response.getCatalogVersion());
            structured.setRuleVersion(response.getRuleVersion());
            return structured;
        } catch (Exception exception) {
            log.warn("Could not restore structured data for a completed AI message");
            return null;
        }
    }

    private static String responseAad(Long ownerUserId, String turnId) {
        return ownerUserId + ":" + turnId;
    }

    private AiAssistantChatResponseVO processingResponse(String sessionId, String clientMessageId, String turnId) {
        AiAssistantChatResponseVO response = new AiAssistantChatResponseVO();
        response.setSessionId(sessionId);
        response.setRequestStatus("PROCESSING");
        response.setClientMessageId(clientMessageId);
        response.setTurnId(turnId);
        return response;
    }

    private AiAssistantChatResponseVO failedResponse(String sessionId, String clientMessageId, String turnId) {
        AiAssistantChatResponseVO response = new AiAssistantChatResponseVO();
        response.setSessionId(sessionId);
        response.setRequestStatus("FAILED");
        response.setClientMessageId(clientMessageId);
        response.setTurnId(turnId);
        return response;
    }

    private String serialize(AiAssistantChatResponseVO response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize AI response for idempotency", exception);
        }
    }

    private static boolean isUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void validateSelectedPolicy(String policyId, String userId) {
        if (!StringUtils.hasText(policyId)) return;
        if (!policyId.matches("[0-9]{1,19}") || ownPolicyQueryService == null) {
            throw new ProjectException(BaseEnum.VALID_EXCEPTION);
        }
        AiOwnPolicyDetailResponseVO detail = ownPolicyQueryService.findOwnPolicy(userId, policyId);
        if (!"FOUND".equals(detail.getStatus())) throw new ProjectException(BaseEnum.VALID_EXCEPTION);
    }

    public boolean deleteConversation(String clientSessionId, UserVO currentUser) {
        if (currentUser == null || currentUser.getId() == null) throw new ProjectException(AuthEnum.NEED_LOGIN);
        AiConversationService conversationService = conversationServiceProvider.getIfAvailable();
        if (conversationService == null) throw new IllegalStateException("AI conversation persistence is disabled");
        if (!StringUtils.hasText(clientSessionId) || !SESSION_ID_PATTERN.matcher(clientSessionId).matches()) {
            throw new ProjectException(BaseEnum.VALID_EXCEPTION);
        }
        return conversationService.requestDelete(currentUser.getId(), clientSessionId);
    }

}

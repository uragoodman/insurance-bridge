package com.itheima.bolee.insurance.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.bolee.framework.commons.dto.security.UserVO;
import com.itheima.bolee.insurance.dto.ai.AiAssistantChatRequestVO;
import com.itheima.bolee.insurance.dto.ai.AiAssistantChatResponseVO;
import com.itheima.bolee.insurance.pojo.ai.AiTurnDO;
import com.itheima.bolee.insurance.pojo.ai.AiTurnStatus;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Base64;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AgnoAssistantIdempotencyTest {
    private static final String CLIENT_MESSAGE_ID = "11111111-1111-4111-8111-111111111111";
    private static final String TURN_ID = "22222222-2222-4222-8222-222222222222";
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private AiConversationService conversationService;
    private AgnoAssistantService service;
    private ObjectMapper objectMapper;
    private AgnoAuthorizationContextService authorizationContextService;
    private AgnoOAuthClientCredentials oauthClientCredentials;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        conversationService = mock(AiConversationService.class);
        ObjectProvider<AiConversationService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(conversationService);
        objectMapper = new ObjectMapper();
        authorizationContextService = mock(AgnoAuthorizationContextService.class);
        oauthClientCredentials = mock(AgnoOAuthClientCredentials.class);
        service = new AgnoAssistantService(true, "http://localhost:8000", 1000, 1000, true, KEY,
                authorizationContextService, oauthClientCredentials,
                provider, objectMapper);
    }

    @Test
    public void duplicateProcessingRequestReturnsStatusWithoutCallingAgent() {
        AiTurnDO turn = new AiTurnDO();
        turn.setTurnId(TURN_ID);
        turn.setStatus(AiTurnStatus.PROCESSING);
        when(conversationService.startTurn(eq(7L), eq("session"), eq(CLIENT_MESSAGE_ID), anyString(), eq("hello")))
                .thenReturn(new AiTurnStartResult(AiTurnStartResult.Outcome.EXISTING, turn));
        AiAssistantChatResponseVO response = service.chat(request(), user());

        assertEquals("PROCESSING", response.getRequestStatus());
        assertEquals(CLIENT_MESSAGE_ID, response.getClientMessageId());
        assertEquals(TURN_ID, response.getTurnId());
        verifyNoInteractions(authorizationContextService, oauthClientCredentials);
    }

    @Test
    public void duplicateCompletedRequestReplaysStoredResponseWithoutCallingAgent() throws Exception {
        AiAssistantChatResponseVO original = new AiAssistantChatResponseVO();
        original.setSessionId("session");
        original.setAnswer("same answer");
        original.setRequestStatus("COMPLETED");
        original.setClientMessageId(CLIENT_MESSAGE_ID);
        original.setTurnId(TURN_ID);
        AiResponseCipher cipher = new AiResponseCipher(KEY);
        AiTurnDO turn = new AiTurnDO();
        turn.setTurnId(TURN_ID);
        turn.setStatus(AiTurnStatus.COMPLETED);
        turn.setEncryptedResponse(cipher.encrypt(objectMapper.writeValueAsString(original), "7:" + TURN_ID));
        when(conversationService.startTurn(eq(7L), eq("session"), eq(CLIENT_MESSAGE_ID), anyString(), eq("hello")))
                .thenReturn(new AiTurnStartResult(AiTurnStartResult.Outcome.EXISTING, turn));

        AiAssistantChatResponseVO replay = service.chat(request(), user());

        assertEquals("same answer", replay.getAnswer());
        assertEquals("COMPLETED", replay.getRequestStatus());
        assertEquals(TURN_ID, replay.getTurnId());
        verify(conversationService, never()).completeTurn(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    public void duplicateFailedRequestIsTerminalAndDoesNotCallAgent() {
        AiTurnDO turn = new AiTurnDO();
        turn.setTurnId(TURN_ID);
        turn.setStatus(AiTurnStatus.FAILED);
        when(conversationService.startTurn(eq(7L), eq("session"), eq(CLIENT_MESSAGE_ID), anyString(), eq("hello")))
                .thenReturn(new AiTurnStartResult(AiTurnStartResult.Outcome.EXISTING, turn));

        AiAssistantChatResponseVO response = service.chat(request(), user());

        assertEquals("FAILED", response.getRequestStatus());
        verifyNoInteractions(authorizationContextService, oauthClientCredentials);
    }

    @Test
    public void duplicateProcessingStreamReturnsStatusWithoutCallingAgent() {
        AiTurnDO turn = new AiTurnDO();
        turn.setTurnId(TURN_ID);
        turn.setStatus(AiTurnStatus.PROCESSING);
        when(conversationService.startTurn(eq(7L), eq("session"), eq(CLIENT_MESSAGE_ID), anyString(), eq("hello")))
                .thenReturn(new AiTurnStartResult(AiTurnStartResult.Outcome.EXISTING, turn));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.streamChat(request(), user(), output);

        assertTrue(output.toString().contains("event: processing"));
        assertTrue(output.toString().contains(TURN_ID));
        verifyNoInteractions(authorizationContextService, oauthClientCredentials);
    }

    @Test
    public void clientMessageIdIsRequired() {
        AiAssistantChatRequestVO request = request();
        request.setClientMessageId(null);
        try {
            service.chat(request, user());
            fail("expected missing id to be rejected");
        } catch (RuntimeException expected) {
            verify(conversationService, never()).startTurn(anyLong(), anyString(), anyString(), anyString(), anyString());
        }
    }

    private AiAssistantChatRequestVO request() {
        AiAssistantChatRequestVO request = new AiAssistantChatRequestVO();
        request.setSessionId("session");
        request.setClientMessageId(CLIENT_MESSAGE_ID);
        request.setMessage("hello");
        return request;
    }

    private UserVO user() {
        UserVO user = new UserVO();
        user.setId(7L);
        return user;
    }

}

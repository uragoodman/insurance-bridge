package com.itheima.bolee.security.web;

import com.itheima.bolee.framework.commons.basic.ResponseResult;
import com.itheima.bolee.security.service.AgentOSAdminTokenService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AgentOSAdminTokenControllerTest {

    @Mock
    private AgentOSAdminTokenService agentOSAdminTokenService;

    @InjectMocks
    private AgentOSAdminTokenController controller;

    @Test
    public void returnsNoStoreTokenResponseForAuthorizedSession() {
        Map<String, Object> token = Collections.<String, Object>singletonMap("access_token", "signed-token");
        when(agentOSAdminTokenService.exchange(null, "active-session")).thenReturn(token);

        ResponseEntity<ResponseResult<Map<String, Object>>> response =
                controller.issueAdminToken(null, "active-session");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("no-store, private", response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("no-cache", response.getHeaders().getFirst(HttpHeaders.PRAGMA));
        assertNotNull(response.getBody());
        assertEquals(Integer.valueOf(200), response.getBody().getCode());
        assertEquals(token, response.getBody().getData());
        verify(agentOSAdminTokenService).exchange(null, "active-session");
    }

    @Test
    public void mapsMissingSessionTo401WithoutIssuingToken() {
        when(agentOSAdminTokenService.exchange(null, null))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "session required"));

        ResponseEntity<ResponseResult<Map<String, Object>>> response =
                controller.issueAdminToken(null, null);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Integer.valueOf(22003), response.getBody().getCode());
        assertTrue(response.getBody().getData().isEmpty());
    }

    @Test
    public void mapsUnapprovedRoleTo403() {
        when(agentOSAdminTokenService.exchange("Bearer user-token", null))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "role denied"));

        ResponseEntity<ResponseResult<Map<String, Object>>> response =
                controller.issueAdminToken("Bearer user-token", null);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(Integer.valueOf(22001), response.getBody().getCode());
        assertTrue(response.getBody().getData().isEmpty());
    }
}

package com.itheima.bolee.insurance.service.ai;

import org.junit.Before;
import org.junit.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class AgnoOAuthTokenValidatorTest {
    private JwtDecoder decoder;
    private AgnoOAuthTokenValidator validator;
    private Jwt validIndexerJwt;

    @Before
    public void setUp() {
        decoder = mock(JwtDecoder.class);
        validator = new AgnoOAuthTokenValidator("http://unused/token_key", "chat-tools");
        ReflectionTestUtils.setField(validator, "decoder", decoder);
        Instant now = Instant.now();
        validIndexerJwt = Jwt.withTokenValue("valid-token")
                .header("alg", "RS256")
                .claim("client_id", "terms-indexer")
                .claim("scope", "product.terms.index")
                .issuedAt(now.minusSeconds(5))
                .expiresAt(now.plusSeconds(120))
                .build();
        when(decoder.decode("valid-token")).thenReturn(validIndexerJwt);
    }

    @Test
    public void dedicatedIndexerClientAndScopeAreAccepted() {
        validator.require("valid-token", "product.terms.index", "terms-indexer");

        verify(decoder).decode("valid-token");
    }

    @Test
    public void chatClientCannotUseTheIndexerScopeEvenWhenScopeClaimMatches() {
        ResponseStatusException exception = expectDenied(
                () -> validator.require("valid-token", "product.terms.index", "chat-tools"));

        assertEquals(403, exception.getStatus().value());
    }

    @Test
    public void indexerCannotUseAReadScopeItWasNotGranted() {
        ResponseStatusException exception = expectDenied(
                () -> validator.require("valid-token", "product.terms.read", "terms-indexer"));

        assertEquals(403, exception.getStatus().value());
    }

    @Test
    public void expiredTokenIsRejected() {
        Instant now = Instant.now();
        Jwt expired = Jwt.withTokenValue("expired-token")
                .header("alg", "RS256")
                .claim("client_id", "terms-indexer")
                .claim("scope", Collections.singleton("product.terms.index"))
                .issuedAt(now.minusSeconds(120))
                .expiresAt(now.minusSeconds(1))
                .build();
        when(decoder.decode("expired-token")).thenReturn(expired);

        ResponseStatusException exception = expectDenied(
                () -> validator.require("expired-token", "product.terms.index", "terms-indexer"));

        assertEquals(401, exception.getStatus().value());
    }

    private ResponseStatusException expectDenied(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected OAuth authorization to be rejected");
        } catch (ResponseStatusException exception) {
            return exception;
        }
    }
}

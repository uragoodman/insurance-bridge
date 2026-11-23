package com.itheima.bolee.insurance.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;

/** Validates OAuth2 client-credentials JWTs issued for Agno's Java tool API. */
@Component
public class AgnoOAuthTokenValidator {

    private final String tokenKeyUrl;
    private final String expectedClientId;
    private volatile JwtDecoder decoder;

    public AgnoOAuthTokenValidator(
            @Value("${agno.oauth.tools-token-key-url:http://127.0.0.1:7078/oauth/token_key}") String tokenKeyUrl,
            @Value("${agno.oauth.tools-client-id:}") String expectedClientId) {
        this.tokenKeyUrl = tokenKeyUrl;
        this.expectedClientId = expectedClientId;
    }

    public void require(String token, String requiredScope) {
        require(token, requiredScope, expectedClientId);
    }

    public void require(String token, String requiredScope, String clientIdExpectedForScope) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(requiredScope)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid agent access token");
        }
        if (!StringUtils.hasText(tokenKeyUrl) || !StringUtils.hasText(clientIdExpectedForScope)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Agent OAuth is not configured");
        }

        final JwtDecoder tokenDecoder;
        try {
            tokenDecoder = decoder();
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OAuth verification key is unavailable");
        }
        final Jwt jwt;
        try {
            jwt = tokenDecoder.decode(token);
        } catch (JwtException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired agent access token");
        }
        if (jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired agent access token");
        }

        String clientId = jwt.getClaimAsString("client_id");
        if (!clientIdExpectedForScope.equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent OAuth client is not permitted");
        }
        Object scopeClaim = jwt.getClaims().get("scope");
        boolean hasScope = scopeClaim instanceof Collection
                ? ((Collection<?>) scopeClaim).contains(requiredScope)
                : scopeClaim instanceof String && java.util.Arrays.asList(((String) scopeClaim).split("\\s+"))
                        .contains(requiredScope);
        if (!hasScope) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent OAuth scope denied");
        }
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (decoder == null) {
                decoder = createDecoder();
            }
            return decoder;
        }
    }

    private JwtDecoder createDecoder() {
        try {
            JsonNode keyResponse = new RestTemplate().getForObject(tokenKeyUrl, JsonNode.class);
            String publicKeyPem = keyResponse == null || keyResponse.get("value") == null
                    ? null : keyResponse.get("value").asText();
            if (!StringUtils.hasText(publicKeyPem)) {
                throw new IllegalStateException("OAuth token key endpoint returned no public key");
            }
            String encoded = publicKeyPem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(encoded.getBytes(StandardCharsets.US_ASCII));
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        } catch (RestClientException | java.security.GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load the OAuth token verification key", exception);
        }
    }
}

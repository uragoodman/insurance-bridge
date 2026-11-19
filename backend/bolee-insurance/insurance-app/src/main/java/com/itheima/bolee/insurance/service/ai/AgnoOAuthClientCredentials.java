package com.itheima.bolee.insurance.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Acquires and caches a short-lived machine token for Java-to-Agno chat calls. */
@Component
public class AgnoOAuthClientCredentials {

    private static final Logger log = LoggerFactory.getLogger(AgnoOAuthClientCredentials.class);

    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final RestTemplate restTemplate;
    private String accessToken;
    private long expiresAtMillis;

    public AgnoOAuthClientCredentials(
            @Value("${agno.oauth.token-url:http://127.0.0.1:7078/oauth/service-token}") String tokenUrl,
            @Value("${agno.oauth.chat-client-id:}") String clientId,
            @Value("${agno.oauth.chat-client-secret:}") String clientSecret,
            @Value("${agno.oauth.chat-scope:agent.chat}") String scope,
            @Value("${agno.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${agno.read-timeout-ms:120000}") int readTimeoutMs) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(Math.min(readTimeoutMs, 10000));
        this.restTemplate = new RestTemplate(factory);
    }

    public synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (StringUtils.hasText(accessToken) && now < expiresAtMillis - 30000) {
            return accessToken;
        }
        if (!StringUtils.hasText(tokenUrl) || !StringUtils.hasText(clientId)
                || !StringUtils.hasText(clientSecret) || !StringUtils.hasText(scope)) {
            throw new IllegalStateException("Agno OAuth client credentials are not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, clientSecret);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", scope);
        try {
            Map<?, ?> response = restTemplate.postForObject(tokenUrl, new HttpEntity<>(form, headers), Map.class);
            Object token = response == null ? null : response.get("access_token");
            Object expiresIn = response == null ? null : response.get("expires_in");
            if (!(token instanceof String) || !StringUtils.hasText((String) token) || !(expiresIn instanceof Number)) {
                throw new IllegalStateException("OAuth server returned an invalid Agno access token");
            }
            long lifetimeSeconds = ((Number) expiresIn).longValue();
            if (lifetimeSeconds <= 0) {
                throw new IllegalStateException("OAuth server returned an invalid Agno token lifetime");
            }
            accessToken = (String) token;
            expiresAtMillis = System.currentTimeMillis() + lifetimeSeconds * 1000;
            return accessToken;
        } catch (RestClientException exception) {
            Throwable rootCause = exception;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            java.net.URI endpoint = java.net.URI.create(tokenUrl);
            log.warn("Agno OAuth token request failed (host={}, port={}, causeType={}, causeMessage={})",
                    endpoint.getHost(), endpoint.getPort(), rootCause.getClass().getSimpleName(),
                    rootCause.getMessage());
            throw new IllegalStateException("Could not obtain an Agno OAuth access token", exception);
        }
    }
}

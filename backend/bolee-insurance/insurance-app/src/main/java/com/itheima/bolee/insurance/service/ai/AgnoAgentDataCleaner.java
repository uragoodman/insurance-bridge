package com.itheima.bolee.insurance.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** OAuth-protected adapter that asks AgentOS to remove one session and its traces. */
@Component
@ConditionalOnProperty(prefix = "ai.persistence", name = "enabled", havingValue = "true")
public class AgnoAgentDataCleaner implements AiAgentDataCleaner {
    private final String serviceUrl;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final RestTemplate restTemplate;
    private String accessToken;
    private long expiresAtMillis;

    public AgnoAgentDataCleaner(
            @Value("${agno.service-url:http://127.0.0.1:8000}") String serviceUrl,
            @Value("${agno.oauth.token-url:http://127.0.0.1:7078/oauth/service-token}") String tokenUrl,
            @Value("${agno.oauth.operator-client-id:}") String clientId,
            @Value("${agno.oauth.operator-client-secret:}") String clientSecret,
            @Value("${agno.oauth.operator-scope:agent.operator}") String scope,
            @Value("${agno.oauth.chat-client-id:}") String chatClientId,
            @Value("${agno.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${agno.cleanup-read-timeout-ms:10000}") int readTimeoutMs) {
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.trim().replaceAll("/+$", "");
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("Agno cleanup timeouts must be greater than zero");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        if (!StringUtils.hasText(this.serviceUrl) || !StringUtils.hasText(tokenUrl)
                || !StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)
                || !"agent.operator".equals(scope)
                || clientId.equals(chatClientId)) {
            throw new IllegalStateException("Agno cleanup OAuth configuration is required when AI persistence is enabled");
        }
    }

    @Override
    public void deleteSessionAndTraces(String agnoSessionId) {
        if (!StringUtils.hasText(agnoSessionId) || agnoSessionId.length() > 64) {
            throw new IllegalArgumentException("Agno session ID is invalid");
        }
        String encoded = UriUtils.encodePathSegment(agnoSessionId, StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        restTemplate.exchange(serviceUrl + "/internal/sessions/" + encoded,
                HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }

    @Override
    public void deleteExpiredTraces(int retentionDays) {
        if (retentionDays <= 0) throw new IllegalArgumentException("Trace retention must be positive");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        restTemplate.exchange(serviceUrl + "/internal/retention/traces?retention_days=" + retentionDays,
                HttpMethod.POST, new HttpEntity<>(headers), Void.class);
    }

    @Override
    public void deleteExpiredSessions(int retentionDays) {
        if (retentionDays <= 0) throw new IllegalArgumentException("Conversation retention must be positive");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        restTemplate.exchange(serviceUrl + "/internal/retention/sessions?retention_days=" + retentionDays,
                HttpMethod.POST, new HttpEntity<>(headers), Void.class);
    }

    private synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (StringUtils.hasText(accessToken) && now < expiresAtMillis - 30000) return accessToken;
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, clientSecret);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED);
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", scope);
        Map<?, ?> response = restTemplate.postForObject(tokenUrl, new HttpEntity<>(form, headers), Map.class);
        Object token = response == null ? null : response.get("access_token");
        Object expiresIn = response == null ? null : response.get("expires_in");
        if (!(token instanceof String) || !StringUtils.hasText((String) token)
                || !(expiresIn instanceof Number) || ((Number) expiresIn).longValue() <= 0) {
            throw new IllegalStateException("OAuth server returned an invalid cleanup token");
        }
        accessToken = (String) token;
        expiresAtMillis = now + ((Number) expiresIn).longValue() * 1000;
        return accessToken;
    }
}

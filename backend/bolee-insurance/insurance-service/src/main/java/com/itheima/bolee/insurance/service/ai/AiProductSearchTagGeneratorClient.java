package com.itheima.bolee.insurance.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** OAuth-protected operator client for grounded AI product search tag generation. */
@Component
public class AiProductSearchTagGeneratorClient {
    private final String serviceUrl;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final String chatClientId;
    private final String operatorClientId;
    private final RestTemplate restTemplate;
    private String accessToken;
    private long expiresAtMillis;

    public AiProductSearchTagGeneratorClient(
            @Value("${agno.service-url:http://127.0.0.1:8000}") String serviceUrl,
            @Value("${agno.oauth.token-url:http://127.0.0.1:7078/oauth/service-token}") String tokenUrl,
            @Value("${agno.oauth.tag-generation-client-id:}") String clientId,
            @Value("${agno.oauth.tag-generation-client-secret:}") String clientSecret,
            @Value("${agno.oauth.tag-generation-scope:product.tags.generate}") String scope,
            @Value("${agno.oauth.operator-client-id:}") String operatorClientId,
            @Value("${agno.oauth.chat-client-id:}") String chatClientId,
            @Value("${agno.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${agno.operator-read-timeout-ms:35000}") int readTimeoutMs) {
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.trim().replaceAll("/+$", "");
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope;
        this.operatorClientId = operatorClientId;
        this.chatClientId = chatClientId;
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("Agno tag generation timeouts must be greater than zero");
        }
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    public GeneratedTags generate(Map<String, String> product) {
        if (!StringUtils.hasText(serviceUrl) || !StringUtils.hasText(tokenUrl)
                || !StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)
                || !"product.tags.generate".equals(scope) || clientId.equals(chatClientId)
                || clientId.equals(operatorClientId)) {
            throw new IllegalStateException("A distinct Agno tag-generation OAuth client is required");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        JsonNode body;
        try {
            body = restTemplate.postForObject(serviceUrl + "/internal/product-tags/generate",
                    new HttpEntity<Map<String, String>>(product, headers), JsonNode.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("AI product tag generation is unavailable", exception);
        }
        if (body == null || !body.path("tags").isArray()) {
            throw new IllegalStateException("AI product tag generator returned an invalid response");
        }
        List<AiSearchTagDTO> tags = new ArrayList<AiSearchTagDTO>();
        for (JsonNode item : body.path("tags")) {
            if (tags.size() >= 20) throw new IllegalStateException("AI product tag generator returned too many tags");
            AiSearchTagDTO tag = new AiSearchTagDTO();
            tag.setTagType(item.path("tag_type").asText(null));
            tag.setTagValue(item.path("tag_value").asText(null));
            tag.setEvidence(item.path("evidence").asText(null));
            tags.add(tag);
        }
        String modelId = body.path("model_id").asText(null);
        String promptVersion = body.path("prompt_version").asText(null);
        if (!StringUtils.hasText(modelId) || !StringUtils.hasText(promptVersion)) {
            throw new IllegalStateException("AI product tag generator returned incomplete metadata");
        }
        return new GeneratedTags(tags, modelId, promptVersion);
    }

    private synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (StringUtils.hasText(accessToken) && now < expiresAtMillis - 30000) return accessToken;
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(clientId, clientSecret);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("scope", scope);
        Map<?, ?> response;
        try {
            response = restTemplate.postForObject(tokenUrl, new HttpEntity<Object>(form, headers), Map.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("Could not obtain Agno operator credentials", exception);
        }
        Object token = response == null ? null : response.get("access_token");
        Object expiresIn = response == null ? null : response.get("expires_in");
        if (!(token instanceof String) || !StringUtils.hasText((String) token)
                || !(expiresIn instanceof Number) || ((Number) expiresIn).longValue() <= 0) {
            throw new IllegalStateException("OAuth server returned an invalid Agno operator token");
        }
        accessToken = (String) token;
        expiresAtMillis = now + ((Number) expiresIn).longValue() * 1000;
        return accessToken;
    }

    public static final class GeneratedTags {
        private final List<AiSearchTagDTO> tags;
        private final String modelId;
        private final String promptVersion;

        private GeneratedTags(List<AiSearchTagDTO> tags, String modelId, String promptVersion) {
            this.tags = tags;
            this.modelId = modelId;
            this.promptVersion = promptVersion;
        }

        public List<AiSearchTagDTO> getTags() { return tags; }
        public String getModelId() { return modelId; }
        public String getPromptVersion() { return promptVersion; }
    }
}

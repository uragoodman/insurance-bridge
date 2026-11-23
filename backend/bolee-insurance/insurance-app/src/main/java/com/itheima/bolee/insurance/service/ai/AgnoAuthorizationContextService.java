package com.itheima.bolee.insurance.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itheima.bolee.insurance.dto.ai.AgnoAuthorizationContext;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Issues short-lived opaque grants; Java remains the authority for resolving user identity and scopes. */
@Service
public class AgnoAuthorizationContextService {

    private static final String REDIS_KEY_PREFIX = "bolee:agno:authorization-context:";
    private static final long GRANT_TTL_MINUTES = 5;
    private static final int TOKEN_BYTES = 32;

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public AgnoAuthorizationContextService(RedissonClient redissonClient, ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Issues a short-lived per-chat-turn grant for read-only product and own-policy tools.
     * Each data capability is still enforced by its endpoint-specific scope.
     */
    public String issue(String userId, String sessionId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("Agno authorization context requires user and session IDs");
        }

        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        AgnoAuthorizationContext context = new AgnoAuthorizationContext(
                userId,
                sessionId,
                new HashSet<String>(Arrays.asList(
                        AgnoScope.PRODUCT_CATALOG_READ,
                        AgnoScope.POLICY_OWN_READ,
                        AgnoScope.PRODUCT_TERMS_READ)),
                Instant.now().plusSeconds(TimeUnit.MINUTES.toSeconds(GRANT_TTL_MINUTES)).getEpochSecond());

        try {
            RBucket<String> bucket = redissonClient.getBucket(redisKey(token), StringCodec.INSTANCE);
            bucket.set(objectMapper.writeValueAsString(context), GRANT_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize Agno authorization context", exception);
        }
        return token;
    }

    /** Resolves a grant for a future internal Java tool endpoint and enforces its required scope. */
    public AgnoAuthorizationContext require(String token, String requiredScope) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(requiredScope)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid agent authorization context");
        }

        RBucket<String> bucket = redissonClient.getBucket(redisKey(token), StringCodec.INSTANCE);
        String value = bucket.get();
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired agent authorization context");
        }

        final AgnoAuthorizationContext context;
        try {
            context = objectMapper.readValue(value, AgnoAuthorizationContext.class);
        } catch (JsonProcessingException exception) {
            bucket.delete();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid agent authorization context");
        }

        if (context.getExpiresAtEpochSecond() <= Instant.now().getEpochSecond()) {
            bucket.delete();
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired agent authorization context");
        }

        Set<String> scopes = context.getScopes();
        if (scopes == null || !scopes.contains(requiredScope)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Agent authorization scope denied");
        }
        return context;
    }

    private String redisKey(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return REDIS_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

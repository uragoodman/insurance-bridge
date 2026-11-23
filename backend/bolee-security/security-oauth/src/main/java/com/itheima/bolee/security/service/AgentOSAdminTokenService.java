package com.itheima.bolee.security.service;

import com.itheima.bolee.framework.commons.constant.basic.SuperConstant;
import com.itheima.bolee.framework.commons.constant.security.OauthCacheConstant;
import com.itheima.bolee.framework.commons.constant.security.OauthConstant;
import com.itheima.bolee.framework.commons.dto.security.RoleVO;
import com.itheima.bolee.framework.commons.dto.security.UserVO;
import com.itheima.bolee.security.feign.UserFeign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Exchanges an authenticated admin-console token for a narrow AgentOS management token. */
@Service
public class AgentOSAdminTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentOSAdminTokenService.class);

    private static final int TOKEN_TTL_SECONDS = 300;
    private static final String AGENTOS_ADMIN_ROLE = "AGENT_ADMIN";
    private static final String AGENTOS_OBSERVER_ROLE = "AGENT_OBSERVER";
    private static final String AGENTOS_CONVERSATION_AUDITOR_ROLE = "CONVERSATION_AUDITOR";

    private final JwtAccessTokenConverter tokenConverter;
    private final JwtTokenStore tokenStore;
    private final RedissonClient redissonClient;
    private final UserFeign userFeign;
    private final String sourceClientId;
    private final String issuer;
    private final String audience;
    private final String auditorRoleLabel;
    private final Set<String> adminRoleLabels;

    public AgentOSAdminTokenService(
            JwtAccessTokenConverter tokenConverter,
            RedissonClient redissonClient,
            UserFeign userFeign,
            @Value("${agentos.authorization.admin-token.source-client-id:insurance-mgt}") String sourceClientId,
            @Value("${agentos.authorization.admin-token.issuer:bolee-security-oauth}") String issuer,
            @Value("${agentos.authorization.admin-token.audience:bolee-insurance-agent-os}") String audience,
            @Value("${agentos.authorization.admin-token.auditor-role-label:agent_conversation_auditor}")
                    String auditorRoleLabel,
            @Value("${agentos.authorization.admin-token.admin-role-labels:admin,superman}")
                    String adminRoleLabels) {
        this.tokenConverter = tokenConverter;
        this.tokenStore = new JwtTokenStore(tokenConverter);
        this.redissonClient = redissonClient;
        this.userFeign = userFeign;
        this.sourceClientId = sourceClientId;
        this.issuer = issuer;
        this.audience = audience;
        this.auditorRoleLabel = auditorRoleLabel.trim().toLowerCase(Locale.ROOT);
        this.adminRoleLabels = parseLabels(adminRoleLabels);
    }

    public Map<String, Object> exchange(String authorizationHeader, String userTokenHeader) {
        String sourceToken = extractBearerToken(authorizationHeader);
        String userToken = StringUtils.hasText(userTokenHeader) ? userTokenHeader.trim() : null;
        if (sourceToken == null && userToken != null) {
            try {
                sourceToken = redissonClient.<String>getBucket(
                        OauthCacheConstant.ACCESS_TOKEN + userToken).get();
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not load AgentOS source session token");
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "User session verification is unavailable");
            }
        }
        if (sourceToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Active user session required");
        }

        final OAuth2Authentication sourceAuthentication;
        final OAuth2AccessToken sourceAccessToken;
        try {
            sourceAuthentication = tokenStore.readAuthentication(sourceToken);
            sourceAccessToken = tokenStore.readAccessToken(sourceToken);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user access token");
        }
        if (sourceAuthentication == null || sourceAuthentication.isClientOnly()
                || sourceAuthentication.getOAuth2Request() == null
                || sourceAccessToken == null || sourceAccessToken.getExpiration() == null
                || sourceAccessToken.isExpired()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired user access token");
        }
        if (!StringUtils.hasText(sourceClientId)
                || !sourceClientId.equals(sourceAuthentication.getOAuth2Request().getClientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token client cannot access AgentOS management");
        }

        Map<String, Object> sourceClaims = sourceAccessToken.getAdditionalInformation();
        String subject = asText(sourceClaims.get(OauthConstant.USER_ID_KEY));
        if (subject == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User identity is missing from access token");
        }
        String sourceJti = asText(sourceClaims.get(OauthConstant.JTI_KEY));
        if (sourceJti == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session identifier is missing");
        }
        if (userToken != null && !userToken.equals(sourceJti)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User session does not match access token");
        }
        requireActiveUserSession(sourceToken, sourceJti, subject);

        Long userId;
        try {
            userId = Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User identity is invalid");
        }
        Set<String> delegatedRoles = loadCurrentRoles(userId);
        if (delegatedRoles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no AgentOS management role");
        }
        if (!StringUtils.hasText(issuer) || !StringUtils.hasText(audience)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AgentOS management token configuration is unavailable");
        }

        Instant now = Instant.now();
        DefaultOAuth2AccessToken delegatedToken = new DefaultOAuth2AccessToken(UUID.randomUUID().toString());
        delegatedToken.setExpiration(Date.from(now.plusSeconds(TOKEN_TTL_SECONDS)));
        delegatedToken.setScope(Collections.singleton("agentos.management"));

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", audience);
        claims.put("sub", subject);
        claims.put("roles", new ArrayList<>(delegatedRoles));
        claims.put("iat", now.getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());
        delegatedToken.setAdditionalInformation(claims);

        OAuth2AccessToken signedToken = tokenConverter.enhance(delegatedToken, sourceAuthentication);
        LOGGER.info("Issued AgentOS management token subject={} roles={}", subject, delegatedRoles);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token", signedToken.getValue());
        response.put("token_type", "Bearer");
        response.put("expires_in", TOKEN_TTL_SECONDS);
        response.put("audience", audience);
        return response;
    }

    private void requireActiveUserSession(String sourceToken, String jti, String subject) {
        try {
            RBucket<String> accessTokenBucket = redissonClient.getBucket(OauthCacheConstant.ACCESS_TOKEN + jti);
            RBucket<UserVO> userSessionBucket = redissonClient.getBucket(OauthCacheConstant.USER_TOKEN + jti);
            String activeToken = accessTokenBucket.get();
            UserVO userSession = userSessionBucket.get();
            if (!sourceToken.equals(activeToken) || userSession == null || userSession.getId() == null
                    || !subject.equals(String.valueOf(userSession.getId()))
                    || !sourceClientId.equals(userSession.getClientId())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "User session is revoked or no longer active");
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not verify AgentOS source session subject={}", subject);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "User session verification is unavailable");
        }
    }

    private Set<String> loadCurrentRoles(Long userId) {
        final UserVO user;
        final List<RoleVO> userRoles;
        try {
            user = userFeign.findUserById(userId);
            userRoles = userFeign.findRoleByUserId(userId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not load current AgentOS roles userId={}", userId);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Current user authorization is unavailable");
        }
        if (user == null || userRoles == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Current user authorization is unavailable");
        }
        if (!SuperConstant.DATA_STATE_0.equals(user.getDataState())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is disabled");
        }
        Set<String> currentRoleLabels = userRoles.stream()
                .filter(role -> role != null && role.getLabel() != null)
                .map(RoleVO::getLabel)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> normalized = new LinkedHashSet<>();
        for (String label : currentRoleLabels) {
            normalized.add(label.trim().toLowerCase(Locale.ROOT));
        }
        return mapRoles(normalized);
    }

    private Set<String> mapRoles(Collection<?> claim) {
        if (claim == null) {
            return Collections.emptySet();
        }
        Set<String> sourceRoles = new LinkedHashSet<>();
        for (Object value : (Collection<?>) claim) {
            if (value instanceof String) {
                sourceRoles.add(((String) value).trim().toLowerCase(Locale.ROOT));
            }
        }
        Set<String> roles = new LinkedHashSet<>();
        if (sourceRoles.stream().anyMatch(adminRoleLabels::contains)) {
            roles.add(AGENTOS_ADMIN_ROLE);
            roles.add(AGENTOS_OBSERVER_ROLE);
        }
        if (sourceRoles.contains("operator")) {
            roles.add(AGENTOS_OBSERVER_ROLE);
        }
        if (!StringUtils.hasText(auditorRoleLabel)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AgentOS auditor role mapping is not configured");
        }
        if (sourceRoles.contains(auditorRoleLabel)) {
            roles.add(AGENTOS_CONVERSATION_AUDITOR_ROLE);
        }
        return roles;
    }

    private static String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)
                || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Set<String> parseLabels(String labels) {
        if (!StringUtils.hasText(labels)) {
            return Collections.emptySet();
        }
        Set<String> parsed = new LinkedHashSet<>();
        for (String label : labels.split(",")) {
            if (StringUtils.hasText(label)) {
                parsed.add(label.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(parsed);
    }
}

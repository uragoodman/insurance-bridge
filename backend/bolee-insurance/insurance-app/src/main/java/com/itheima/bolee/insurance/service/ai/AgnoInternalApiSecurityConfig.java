package com.itheima.bolee.insurance.service.ai;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Protects Java tool APIs with OAuth2 service identity and per-chat authorization. */
@Configuration
public class AgnoInternalApiSecurityConfig implements WebMvcConfigurer, HandlerInterceptor {

    private final AgnoOAuthTokenValidator oauthTokenValidator;
    private final AgnoAuthorizationContextService authorizationContextService;

    public AgnoInternalApiSecurityConfig(
            AgnoOAuthTokenValidator oauthTokenValidator,
            AgnoAuthorizationContextService authorizationContextService) {
        this.oauthTokenValidator = oauthTokenValidator;
        this.authorizationContextService = authorizationContextService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/internal/agent-tools/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AgentScope requiredScope = requiredScope(handler);
        if (requiredScope == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        oauthTokenValidator.require(authorization.substring(7).trim(), requiredScope.value());
        authorizationContextService.require(
                request.getHeader("X-Agent-Authorization-Context"), requiredScope.value());
        return true;
    }

    private AgentScope requiredScope(Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return null;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        AgentScope scope = handlerMethod.getMethodAnnotation(AgentScope.class);
        return scope == null ? handlerMethod.getBeanType().getAnnotation(AgentScope.class) : scope;
    }
}

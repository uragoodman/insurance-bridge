package com.itheima.bolee.insurance.service.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Protects asynchronous indexer callbacks with its own OAuth client identity and scope. */
@Configuration
public class AgnoIndexApiSecurityConfig implements WebMvcConfigurer, HandlerInterceptor {
    private final AgnoOAuthTokenValidator tokenValidator;
    private final String indexerClientId;

    public AgnoIndexApiSecurityConfig(AgnoOAuthTokenValidator tokenValidator,
                                      @Value("${agno.oauth.indexer-client-id:}") String indexerClientId) {
        this.tokenValidator = tokenValidator;
        this.indexerClientId = indexerClientId;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/internal/agent-index/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        AgentIndexScope required = requiredScope(handler);
        if (required == null) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        tokenValidator.require(authorization.substring(7).trim(), required.value(), indexerClientId);
        return true;
    }

    private AgentIndexScope requiredScope(Object handler) {
        if (!(handler instanceof HandlerMethod)) return null;
        HandlerMethod method = (HandlerMethod) handler;
        AgentIndexScope scope = method.getMethodAnnotation(AgentIndexScope.class);
        return scope == null ? method.getBeanType().getAnnotation(AgentIndexScope.class) : scope;
    }
}

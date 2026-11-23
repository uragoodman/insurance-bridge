package com.itheima.bolee.security.service;

import com.itheima.bolee.security.feign.UserFeign;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RedissonClient;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class AgentOSAdminTokenServiceTest {

    private AgentOSAdminTokenService service;

    @Before
    public void setUp() {
        service = new AgentOSAdminTokenService(
                mock(JwtAccessTokenConverter.class),
                mock(RedissonClient.class),
                mock(UserFeign.class),
                "insurance-mgt",
                "test-issuer",
                "test-audience",
                "agent_conversation_auditor",
                "admin,superman");
    }

    @Test
    public void mapsConfiguredAdministratorLabelsToAdminAndObserver() {
        Set<String> roles = mapRoles("superman", "admin");

        assertEquals(2, roles.size());
        assertTrue(roles.contains("AGENT_ADMIN"));
        assertTrue(roles.contains("AGENT_OBSERVER"));
    }

    @Test
    public void mapsOperatorAndAuditorToLeastPrivilegeRoles() {
        Set<String> operatorRoles = mapRoles("operator");
        Set<String> auditorRoles = mapRoles("agent_conversation_auditor");

        assertEquals(Collections.singleton("AGENT_OBSERVER"), operatorRoles);
        assertEquals(Collections.singleton("CONVERSATION_AUDITOR"), auditorRoles);
    }

    @Test
    public void doesNotGrantAgentOsRolesToUnknownLabels() {
        assertTrue(mapRoles("customer", "vice").isEmpty());
    }

    @SuppressWarnings("unchecked")
    private Set<String> mapRoles(String... labels) {
        return (Set<String>) ReflectionTestUtils.invokeMethod(service, "mapRoles", Arrays.asList(labels));
    }
}

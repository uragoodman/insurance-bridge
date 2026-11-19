package com.itheima.bolee.insurance.service.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AgnoAgentDataCleanerTest {
    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private AgnoAgentDataCleaner cleaner;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/service-token", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            respond(exchange, 200, "{\"access_token\":\"operator-token\",\"expires_in\":300}");
        });
        server.createContext("/internal/sessions", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                    + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 204, "");
        });
        server.createContext("/internal/retention/traces", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                    + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"deleted_rows\":2}");
        });
        server.createContext("/internal/retention/sessions", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                    + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"deleted_sessions\":1}");
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        cleaner = new AgnoAgentDataCleaner(baseUrl, baseUrl + "/oauth/service-token",
                "cleanup-client", "secret", "agent.operator", "chat-client", 1000, 1000);
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    public void deletesSessionAndRequestsConfiguredTraceRetentionWithOperatorToken() {
        cleaner.deleteSessionAndTraces("session-1");
        cleaner.deleteExpiredSessions(90);
        cleaner.deleteExpiredTraces(30);

        assertEquals(4, requests.size());
        assertTrue(requests.get(0).startsWith("POST /oauth/service-token"));
        assertTrue(requests.get(1).contains("DELETE /internal/sessions/session-1 Bearer operator-token"));
        assertTrue(requests.get(2).contains(
                "POST /internal/retention/sessions?retention_days=90 Bearer operator-token"));
        assertTrue(requests.get(3).contains(
                "POST /internal/retention/traces?retention_days=30 Bearer operator-token"));
    }

    @Test
    public void refusesChatClientReuseAndNonOperatorScope() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        assertInvalidConfiguration(baseUrl, "cleanup-client", "secret", "agent.operator", "cleanup-client");
        assertInvalidConfiguration(baseUrl, "cleanup-client", "secret", "agent.chat", "chat-client");
    }

    private static void assertInvalidConfiguration(String baseUrl, String clientId, String secret,
                                                   String scope, String chatClientId) {
        try {
            new AgnoAgentDataCleaner(baseUrl, baseUrl + "/oauth/service-token", clientId,
                    secret, scope, chatClientId, 1000, 1000);
            fail("unsafe cleanup OAuth configuration must fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("cleanup OAuth"));
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}

package com.itheima.bolee.insurance.service.ai;

/** Internal adapter for deleting an Agno session and all associated trace data. */
public interface AiAgentDataCleaner {
    void deleteSessionAndTraces(String agnoSessionId) throws Exception;

    void deleteExpiredTraces(int retentionDays) throws Exception;

    void deleteExpiredSessions(int retentionDays) throws Exception;
}

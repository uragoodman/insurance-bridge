package com.itheima.bolee.insurance.mapper.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.itheima.bolee.insurance.pojo.ai.AiCleanupTaskDO;
import com.itheima.bolee.insurance.pojo.ai.AiCleanupTaskStatus;
import com.itheima.bolee.insurance.pojo.ai.AiConversationDO;
import com.itheima.bolee.insurance.pojo.ai.AiConversationStatus;
import com.itheima.bolee.insurance.pojo.ai.AiTurnDO;
import com.itheima.bolee.insurance.pojo.ai.AiTurnStatus;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Assume;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.Assert.*;

/** Runs against the configured MySQL schema when AI_DB_* variables are supplied. */
public class AiPersistenceMapperMySqlTest {

    @Test
    public void mapperSqlWorksAgainstMigratedInsuranceSchema() throws Exception {
        String url = System.getenv("AI_DB_URL");
        String user = System.getenv("AI_DB_USER");
        String password = System.getenv("AI_DB_PASSWORD");
        Assume.assumeTrue("AI_DB_* is required for the MySQL mapper integration test",
                hasText(url) && hasText(user) && password != null);

        LogFactory.useNoLogging();
        UnpooledDataSource dataSource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("ai-mysql-test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(AiConversationMapper.class);
        configuration.addMapper(AiTurnMapper.class);
        configuration.addMapper(AiCleanupTaskMapper.class);
        SqlSessionFactory sessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);

        long ownerId = positiveId();
        long conversationId = positiveId();
        long turnPk = positiveId();
        long cleanupPk = positiveId();
        String sessionId = UUID.randomUUID().toString();
        String agnoSessionId = UUID.randomUUID().toString();
        String turnId = UUID.randomUUID().toString();
        String clientMessageId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

        try (SqlSession sqlSession = sessionFactory.openSession(false)) {
            AiConversationMapper conversations = sqlSession.getMapper(AiConversationMapper.class);
            AiTurnMapper turns = sqlSession.getMapper(AiTurnMapper.class);
            AiCleanupTaskMapper cleanupTasks = sqlSession.getMapper(AiCleanupTaskMapper.class);

            AiConversationDO conversation = new AiConversationDO();
            conversation.setId(conversationId);
            conversation.setOwnerUserId(ownerId);
            conversation.setSessionId(sessionId);
            conversation.setAgnoSessionId(agnoSessionId);
            conversation.setStatus(AiConversationStatus.ACTIVE);
            conversation.setExpiresAt(now.plusDays(1));
            conversation.setCreateTime(now);
            conversation.setUpdateTime(now);
            conversation.setCreateBy(ownerId);
            conversation.setUpdateBy(ownerId);
            assertEquals(1, conversations.insertIfAbsent(conversation));
            assertEquals(AiConversationStatus.ACTIVE, conversations.findOwned(ownerId, sessionId).getStatus());
            assertTrue(conversations.findExpiredForUpdate(now.minusDays(36500), 100).isEmpty());
            assertEquals(1, conversations.updateActivity(conversationId, ownerId, now, now.plusDays(1)));

            AiTurnDO turn = new AiTurnDO();
            turn.setId(turnPk);
            turn.setOwnerUserId(ownerId);
            turn.setConversationId(conversationId);
            turn.setTurnId(turnId);
            turn.setClientMessageId(clientMessageId);
            turn.setRequestHash("a".repeat(64));
            turn.setTraceId(UUID.randomUUID().toString());
            turn.setStatus(AiTurnStatus.PROCESSING);
            turn.setStartedAt(now);
            turn.setExpiresAt(now.plusDays(1));
            turn.setCreateTime(now);
            turn.setUpdateTime(now);
            turn.setCreateBy(ownerId);
            turn.setUpdateBy(ownerId);
            assertEquals(1, turns.insertIfAbsent(turn));
            assertEquals(AiTurnStatus.PROCESSING, turns.findByIdempotencyKey(ownerId, conversationId, clientMessageId).getStatus());
            assertEquals(1, turns.completeProcessing(ownerId, turnId, "run-smoke", "v1:encrypted-smoke", now.plusSeconds(1)));
            AiTurnDO completed = turns.findByOwnerAndTurnId(ownerId, turnId);
            assertEquals(AiTurnStatus.COMPLETED, completed.getStatus());
            assertEquals("v1:encrypted-smoke", completed.getEncryptedResponse());

            AiTurnDO failedTurn = new AiTurnDO();
            failedTurn.setId(positiveId());
            failedTurn.setOwnerUserId(ownerId);
            failedTurn.setConversationId(conversationId);
            failedTurn.setTurnId(UUID.randomUUID().toString());
            failedTurn.setClientMessageId(UUID.randomUUID().toString());
            failedTurn.setRequestHash("b".repeat(64));
            failedTurn.setTraceId(UUID.randomUUID().toString());
            failedTurn.setStatus(AiTurnStatus.PROCESSING);
            failedTurn.setStartedAt(now);
            failedTurn.setExpiresAt(now.plusDays(1));
            failedTurn.setCreateTime(now);
            failedTurn.setUpdateTime(now);
            failedTurn.setCreateBy(ownerId);
            failedTurn.setUpdateBy(ownerId);
            assertEquals(1, turns.insertIfAbsent(failedTurn));
            assertEquals(1, turns.failProcessing(ownerId, failedTurn.getTurnId(), "TEST_FAILURE", now.plusSeconds(2)));
            assertEquals(AiTurnStatus.FAILED, turns.findByOwnerAndTurnId(ownerId, failedTurn.getTurnId()).getStatus());

            AiCleanupTaskDO task = new AiCleanupTaskDO();
            task.setId(cleanupPk);
            task.setOwnerUserId(ownerId);
            task.setConversationId(conversationId);
            task.setAgnoSessionId(agnoSessionId);
            task.setCreateTime(now);
            task.setUpdateTime(now);
            task.setCreateBy(ownerId);
            task.setUpdateBy(ownerId);
            assertEquals(1, cleanupTasks.insertIfAbsent(task));
            AiTurnDO activeTurn = new AiTurnDO();
            activeTurn.setId(positiveId());
            activeTurn.setOwnerUserId(ownerId);
            activeTurn.setConversationId(conversationId);
            activeTurn.setTurnId(UUID.randomUUID().toString());
            activeTurn.setClientMessageId(UUID.randomUUID().toString());
            activeTurn.setRequestHash("c".repeat(64));
            activeTurn.setTraceId(UUID.randomUUID().toString());
            activeTurn.setStatus(AiTurnStatus.PROCESSING);
            activeTurn.setStartedAt(now.minusMinutes(5));
            activeTurn.setExpiresAt(now.plusDays(1));
            activeTurn.setCreateTime(now);
            activeTurn.setUpdateTime(now);
            activeTurn.setCreateBy(ownerId);
            activeTurn.setUpdateBy(ownerId);
            assertEquals(1, turns.insertIfAbsent(activeTurn));
            assertEquals(1, conversations.markDeletePending(conversationId, ownerId, now));
            assertNull(cleanupTasks.findNextClaimableForUpdate(now));
            assertEquals(1, turns.failAbandonedProcessingForPendingDeletes(now.minusMinutes(3), now));
            AiCleanupTaskDO claimable = cleanupTasks.findNextClaimableForUpdate(now);
            assertNotNull(claimable);
            assertEquals(AiCleanupTaskStatus.PENDING, claimable.getStatus());
            assertEquals(1, cleanupTasks.claim(cleanupPk, now, now.plusMinutes(3)));
            AiCleanupTaskDO claimed = cleanupTasks.findById(cleanupPk);
            assertEquals(AiCleanupTaskStatus.PROCESSING, claimed.getStatus());
            assertEquals(now.plusMinutes(3), claimed.getLeaseUntil());
            assertEquals(1, cleanupTasks.scheduleRetry(cleanupPk, "RETRY_WAIT", now, "TEST_RETRY", now));
            assertEquals(AiCleanupTaskStatus.RETRY_WAIT, cleanupTasks.findById(cleanupPk).getStatus());
            assertEquals(1, cleanupTasks.claim(cleanupPk, now, now.plusMinutes(3)));
            assertEquals(1, cleanupTasks.markCompleted(cleanupPk, now, now.plusDays(30)));
            assertEquals(AiCleanupTaskStatus.COMPLETED, cleanupTasks.findById(cleanupPk).getStatus());
            assertTrue(cleanupTasks.findExpiredCompleted(now.plusDays(31), 10000).stream()
                    .anyMatch(expired -> cleanupPk == expired.getId()));
            assertEquals(1, cleanupTasks.deleteExpiredCompleted(cleanupPk, now.plusDays(31)));
            AiCleanupTaskDO restoredTask = new AiCleanupTaskDO();
            restoredTask.setId(positiveId());
            restoredTask.setOwnerUserId(ownerId);
            restoredTask.setConversationId(conversationId);
            restoredTask.setAgnoSessionId(agnoSessionId);
            restoredTask.setCreateTime(now);
            restoredTask.setUpdateTime(now);
            restoredTask.setCreateBy(ownerId);
            restoredTask.setUpdateBy(ownerId);
            assertEquals(1, cleanupTasks.insertIfAbsent(restoredTask));
            assertEquals(1, cleanupTasks.deleteByAgnoSessionId(agnoSessionId));
            assertEquals(3, turns.deleteByAgnoSessionId(agnoSessionId));
            assertEquals(1, conversations.deleteByAgnoSessionId(agnoSessionId));
            sqlSession.rollback();
        }

        try (SqlSession verification = sessionFactory.openSession()) {
            assertNull(verification.getMapper(AiConversationMapper.class).findOwned(ownerId, sessionId));
            assertNull(verification.getMapper(AiTurnMapper.class).findByOwnerAndTurnId(ownerId, turnId));
            assertNull(verification.getMapper(AiCleanupTaskMapper.class).findById(cleanupPk));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static long positiveId() {
        long id = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        return id == 0 ? 1 : id;
    }
}

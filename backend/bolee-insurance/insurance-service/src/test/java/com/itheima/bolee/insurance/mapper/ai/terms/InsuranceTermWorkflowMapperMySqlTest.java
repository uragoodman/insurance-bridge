package com.itheima.bolee.insurance.mapper.ai.terms;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.itheima.bolee.insurance.mapper.ai.terms.InsuranceTermIndexTaskMapper;
import com.itheima.bolee.insurance.mapper.ai.terms.InsuranceTermSourceMapper;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermIndexTaskDO;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceDO;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Assume;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

/** Runs against a migrated MySQL 5.7+ insurance schema when AI_DB_* are supplied. */
public class InsuranceTermWorkflowMapperMySqlTest {

    @Test
    public void taskLeasesAreFencedAndExpiredPublishingSourcesAreWithdrawn() throws Exception {
        String url = System.getenv("AI_DB_URL");
        String user = System.getenv("AI_DB_USER");
        String password = System.getenv("AI_DB_PASSWORD");
        Assume.assumeTrue("AI_DB_* is required for the MySQL mapper integration test",
                hasText(url) && hasText(user) && password != null);

        LogFactory.useNoLogging();
        UnpooledDataSource dataSource = new UnpooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("term-mysql-test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(InsuranceTermSourceMapper.class);
        configuration.addMapper(InsuranceTermIndexTaskMapper.class);
        SqlSessionFactory sessionFactory = new MybatisSqlSessionFactoryBuilder().build(configuration);

        long base = System.currentTimeMillis() * 10000L + ThreadLocalRandom.current().nextInt(10000);
        long productId = base;
        long profileId = base + 1;
        long expiredSourceId = base + 2;
        long validSourceId = base + 3;
        long scheduledExpiredSourceId = base + 4;
        long draftSourceId = base + 5;
        long inactiveProfileSourceId = base + 6;
        long taskId = base + 7;
        LocalDateTime now = LocalDateTime.now().withNano(0);

        try (SqlSession session = sessionFactory.openSession(true)) {
            InsuranceTermSourceMapper sources = session.getMapper(InsuranceTermSourceMapper.class);
            InsuranceTermIndexTaskMapper tasks = session.getMapper(InsuranceTermIndexTaskMapper.class);
            createProfile(session, profileId, productId, now);
            sources.insertSource(source(expiredSourceId, productId, profileId, "PUBLISHING",
                    LocalDate.now().minusDays(1)));
            sources.insertSource(source(validSourceId, productId, profileId, "PUBLISHING",
                    LocalDate.now().plusDays(1)));
            sources.insertSource(source(scheduledExpiredSourceId, productId, profileId, "PUBLISHING",
                    LocalDate.now().minusDays(1)));
            sources.insertSource(source(draftSourceId, productId, profileId, "DRAFT",
                    LocalDate.now().plusDays(1)));
            try (java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                    "UPDATE tab_insurance_term_source SET index_status = 'INDEXING' WHERE id IN (?, ?, ?)")) {
                statement.setLong(1, expiredSourceId);
                statement.setLong(2, validSourceId);
                statement.setLong(3, scheduledExpiredSourceId);
                statement.executeUpdate();
            }

            List<InsuranceTermSourceDO> expiredCandidates = sources.findSourcesNeedingExpiryCleanup(now, 100);
            assertTrue(expiredCandidates.stream().anyMatch(row -> row.getId().equals(expiredSourceId)));
            assertTrue(expiredCandidates.stream().anyMatch(row -> row.getId().equals(scheduledExpiredSourceId)));
            assertFalse(expiredCandidates.stream().anyMatch(row -> row.getId().equals(validSourceId)));
            assertEquals(1, sources.withdrawExpired(scheduledExpiredSourceId, now));
            assertEquals("WITHDRAWN", sources.findById(scheduledExpiredSourceId).getStatus());

            assertEquals(1, sources.reserveDraftDeletion(draftSourceId, 7L, now));
            assertEquals("DELETING", sources.findById(draftSourceId).getStatus());
            assertTrue(sources.findDeleting(10).stream().anyMatch(row -> row.getId().equals(draftSourceId)));
            assertEquals(1, sources.softDeleteDraft(draftSourceId, 7L, now));
            assertNull(sources.findById(draftSourceId));

            assertEquals(0, sources.markIndexedIfValid(expiredSourceId, "a".repeat(64), now));
            assertEquals(1, sources.withdrawInvalidPublishing(expiredSourceId, "a".repeat(64), now));
            assertEquals("WITHDRAWN", sources.findById(expiredSourceId).getStatus());
            assertEquals(1, sources.markIndexedIfValid(validSourceId, "a".repeat(64), now));
            assertEquals("PUBLISHED", sources.findById(validSourceId).getStatus());

            InsuranceTermIndexTaskDO task = new InsuranceTermIndexTaskDO();
            task.setId(taskId);
            task.setSourceId(validSourceId);
            task.setAction("INDEX");
            task.setSourceSha256("a".repeat(64));
            task.setNextAttemptAt(now.minusSeconds(1));
            task.setCreateTime(now);
            task.setUpdateTime(now);
            assertEquals(1, tasks.insert(task));
            assertNotNull(tasks.findNextClaimable(now));
            assertEquals(1, tasks.claim(taskId, now, now.plusSeconds(60), "first-worker"));
            assertEquals(0, tasks.claim(taskId, now, now.plusSeconds(60), "racing-worker"));

            session.getConnection().createStatement().executeUpdate("UPDATE tab_insurance_term_index_task "
                    + "SET lease_until = DATE_SUB(NOW(3), INTERVAL 1 SECOND) WHERE id = " + taskId);
            LocalDateTime reclaimedAt = LocalDateTime.now().withNano(0);
            assertEquals(0, tasks.complete(taskId, "first-worker", reclaimedAt));
            assertEquals(1, tasks.claim(taskId, reclaimedAt, reclaimedAt.plusSeconds(60), "second-worker"));
            assertEquals(0, tasks.fail(taskId, "FAILED", reclaimedAt, "stale", "first-worker", reclaimedAt));
            assertEquals(1, tasks.complete(taskId, "second-worker", reclaimedAt));

            sources.insertSource(source(inactiveProfileSourceId, productId, profileId, "PUBLISHING",
                    LocalDate.now().plusDays(1)));
            try (java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                    "UPDATE tab_insurance_term_source SET index_status = 'INDEXING' WHERE id = ?")) {
                statement.setLong(1, inactiveProfileSourceId);
                statement.executeUpdate();
            }
            try (java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                    "UPDATE tab_ai_product_profile_version SET status = 'DRAFT' WHERE id = ?")) {
                statement.setLong(1, profileId);
                statement.executeUpdate();
            }
            assertEquals(0, sources.markIndexedIfValid(inactiveProfileSourceId, "a".repeat(64), reclaimedAt));
            assertEquals(1, sources.withdrawInvalidPublishing(inactiveProfileSourceId, "a".repeat(64), reclaimedAt));
            assertEquals("WITHDRAWN", sources.findById(inactiveProfileSourceId).getStatus());
        } finally {
            try (java.sql.Connection connection = dataSource.getConnection();
                 java.sql.Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM tab_insurance_term_index_task WHERE source_id IN ("
                        + expiredSourceId + "," + validSourceId + "," + scheduledExpiredSourceId + ","
                        + draftSourceId + "," + inactiveProfileSourceId + ")");
                statement.executeUpdate("DELETE FROM tab_insurance_term_source WHERE id IN ("
                        + expiredSourceId + "," + validSourceId + "," + scheduledExpiredSourceId + ","
                        + draftSourceId + "," + inactiveProfileSourceId + ")");
                statement.executeUpdate("DELETE FROM tab_ai_product_profile_version WHERE id = " + profileId);
            }
        }
    }

    private void createProfile(SqlSession session, long profileId, long productId, LocalDateTime now)
            throws Exception {
        try (java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                "INSERT INTO tab_ai_product_profile_version "
                        + "(id, product_id, product_name_snapshot, product_version, category_id, status, "
                        + "effective_from, effective_to, create_time, update_time, data_state) "
                        + "VALUES (?, ?, 'term-test', 1, 1, 'PUBLISHED', ?, NULL, ?, ?, '0')")) {
            statement.setLong(1, profileId);
            statement.setLong(2, productId);
            statement.setObject(3, now.minusDays(1));
            statement.setObject(4, now);
            statement.setObject(5, now);
            statement.executeUpdate();
        }
    }

    private InsuranceTermSourceDO source(long id, long productId, long profileId, String status,
                                         LocalDate effectiveTo) {
        InsuranceTermSourceDO source = new InsuranceTermSourceDO();
        source.setId(id);
        source.setInsuranceId(productId);
        source.setProfileVersionId(profileId);
        source.setProductVersion(1);
        source.setSourceType("OFFICIAL_PRODUCT_TERM");
        source.setTitle("term-test");
        source.setOriginalFileName("term.txt");
        source.setContentType("text/plain");
        source.setFileSize(1L);
        source.setSha256("a".repeat(64));
        source.setStorageProvider("MINIO");
        source.setBucketName("test");
        source.setObjectKey("test/term.txt");
        source.setStatus(status);
        source.setEffectiveTo(effectiveTo);
        source.setUploadedBy(1L);
        source.setCreateTime(LocalDateTime.now());
        source.setUpdateTime(LocalDateTime.now());
        return source;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

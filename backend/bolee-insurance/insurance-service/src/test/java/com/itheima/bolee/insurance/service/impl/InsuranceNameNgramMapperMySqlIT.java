package com.itheima.bolee.insurance.service.impl;

import com.itheima.bolee.insurance.mapper.InsuranceNameNgramMapper;
import com.itheima.bolee.insurance.mapper.InsuranceNameNgramMapper.NgramValue;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/** Explicit opt-in integration test for the isolated synthetic MySQL schema. */
public class InsuranceNameNgramMapperMySqlIT {

    private static final String INITIAL_NAME = "团体运动意外保险保障方案";
    private static final String RENAMED_NAME = "团体运动医疗保障计划";
    private static final String SEARCH_TERM = "团体运动意外保险";

    @Test
    public void mapperBindsBinaryTrigramsAndKeepsPostingFrequencyConsistent() throws Exception {
        String url = System.getenv("AI_NGRAM_TEST_DB_URL");
        String username = System.getenv("AI_NGRAM_TEST_DB_USER");
        String password = System.getenv("AI_NGRAM_TEST_DB_PASSWORD");
        assertTrue("AI_NGRAM_TEST_DB_URL must target the loopback isolated database",
            url != null && url.startsWith("jdbc:mysql://127.0.0.1:13316/bolee-insurance-perf"));
        assertNotNull("Set AI_NGRAM_TEST_DB_USER", username);
        assertNotNull("Set AI_NGRAM_TEST_DB_PASSWORD", password);

        long productId = Long.parseLong(System.getenv().getOrDefault(
            "AI_NGRAM_TEST_PRODUCT_ID", "8800000000000000001"));
        SqlSessionFactory factory = newFactory(url, username, password);
        try (SqlSession session = factory.openSession(false)) {
            Connection connection = session.getConnection();
            InsuranceNameNgramMapper mapper = session.getMapper(InsuranceNameNgramMapper.class);
            InsuranceNameNgramIndex index = new InsuranceNameNgramIndex(mapper);
            index.setWriteEnabled(true);
            index.setReadEnabled(true);

            String originalName = readProductName(connection, productId);
            assertNotNull("The synthetic fixture product must exist", originalName);
            try {
                updateProductName(connection, productId, INITIAL_NAME);
                index.replace(productId, INITIAL_NAME);

                NgramValue rarestValue = mapper.selectRarestNgram(InsuranceNameNgramIndex.trigrams(SEARCH_TERM));
                byte[] rarest = rarestValue == null ? null : rarestValue.getNgram();
                assertNotNull("Mapper must bind and read VARBINARY trigram values", rarest);
                Set<Long> legacyIds = searchByLike(connection, SEARCH_TERM);
                Set<Long> indexedIds = searchByCandidateAndLike(connection, rarest, SEARCH_TERM);
                assertEquals("Candidate lookup must preserve the legacy LIKE result set", legacyIds, indexedIds);
                assertTrue("The fixture product should match the Chinese query", indexedIds.contains(productId));
                assertSelectedGramFrequencyMatchesPostings(connection, rarest);

                updateProductName(connection, productId, RENAMED_NAME);
                index.replace(productId, RENAMED_NAME);
                NgramValue oldTermValue = mapper.selectRarestNgram(InsuranceNameNgramIndex.trigrams(SEARCH_TERM));
                byte[] oldTermGram = oldTermValue == null ? null : oldTermValue.getNgram();
                assertNotNull("Other fixture products may still contain the old trigram", oldTermGram);
                assertTrue("Renamed product must leave the old candidate set",
                    !searchByCandidateAndLike(connection, oldTermGram, SEARCH_TERM).contains(productId));
                assertSelectedGramFrequencyMatchesPostings(connection, oldTermGram);

                index.delete(Collections.singletonList(productId));
                assertEquals("Delete must remove all postings for the product", 0,
                    countForProduct(connection, productId));
                assertSelectedGramFrequencyMatchesPostings(connection, oldTermGram);
            } finally {
                session.rollback();
            }
        }
    }

    private static SqlSessionFactory newFactory(String url, String username, String password) {
        PooledDataSource dataSource = new PooledDataSource("com.mysql.cj.jdbc.Driver", url, username, password);
        Environment environment = new Environment("insurance-ngram-integration", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.addMapper(InsuranceNameNgramMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static String readProductName(Connection connection, long productId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT insurance_name FROM tab_insurance WHERE id = ?")) {
            statement.setLong(1, productId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private static void updateProductName(Connection connection, long productId, String name) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE tab_insurance SET insurance_name = ? WHERE id = ?")) {
            statement.setString(1, name);
            statement.setLong(2, productId);
            assertEquals("The fixture product should be updated", 1, statement.executeUpdate());
        }
    }

    private static Set<Long> searchByLike(Connection connection, String keyword) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id FROM tab_insurance WHERE insurance_name LIKE ?")) {
            statement.setString(1, "%" + keyword + "%");
            return readIds(statement);
        }
    }

    private static Set<Long> searchByCandidateAndLike(Connection connection, byte[] gram, String keyword)
        throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT id FROM tab_insurance WHERE id IN " +
                "(SELECT insurance_id FROM tab_insurance_name_ngram WHERE ngram = ?) " +
                "AND insurance_name LIKE ?")) {
            statement.setBytes(1, gram);
            statement.setString(2, "%" + keyword + "%");
            return readIds(statement);
        }
    }

    private static Set<Long> readIds(PreparedStatement statement) throws Exception {
        Set<Long> ids = new HashSet<>();
        try (ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                ids.add(result.getLong(1));
            }
        }
        return ids;
    }

    private static void assertSelectedGramFrequencyMatchesPostings(Connection connection, byte[] gram)
        throws Exception {
        long storedCount = scalarLong(connection,
            "SELECT product_count FROM tab_insurance_name_ngram_stat WHERE ngram = ?", gram);
        long postingCount = scalarLong(connection,
            "SELECT COUNT(DISTINCT insurance_id) FROM tab_insurance_name_ngram WHERE ngram = ?", gram);
        assertEquals("Frequency table must match posting count", postingCount, storedCount);
    }

    private static int countForProduct(Connection connection, long productId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM tab_insurance_name_ngram WHERE insurance_id = ?")) {
            statement.setLong(1, productId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private static long scalarLong(Connection connection, String sql, byte[] gram) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, gram);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }
}

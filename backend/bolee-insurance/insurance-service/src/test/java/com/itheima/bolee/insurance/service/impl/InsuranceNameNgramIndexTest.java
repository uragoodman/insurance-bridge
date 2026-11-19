package com.itheima.bolee.insurance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itheima.bolee.insurance.pojo.Insurance;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class InsuranceNameNgramIndexTest {

    @Test
    public void createsDistinctUtf8TrigramsByCodePoint() {
        List<byte[]> grams = InsuranceNameNgramIndex.trigrams("团体险团");

        assertEquals(2, grams.size());
        assertEquals("团体险", new String(grams.get(0), StandardCharsets.UTF_8));
        assertEquals("体险团", new String(grams.get(1), StandardCharsets.UTF_8));
        assertEquals(2, InsuranceNameNgramIndex.trigrams("团体团体").size());
    }

    @Test
    public void wildcardAndShortQueriesKeepOriginalLikePath() {
        assertFalse(InsuranceNameNgramIndex.isIndexable("团"));
        assertFalse(InsuranceNameNgramIndex.isIndexable("团体"));
        assertFalse(InsuranceNameNgramIndex.isIndexable("团%险"));
        assertFalse(InsuranceNameNgramIndex.isIndexable("团_险"));
        assertFalse(InsuranceNameNgramIndex.isIndexable("团\\险"));
        assertTrue(InsuranceNameNgramIndex.isIndexable("团体险"));
        assertFalse(InsuranceNameNgramIndex.isIndexable("Synthetic"));
        assertFalse(InsuranceNameNgramIndex.isIndexable("团体A"));
        assertFalse(InsuranceNameNgramIndex.isIndexable("团体".repeat(33)));
        assertFalse(InsuranceNameNgramIndex.isIndexable("团体😀"));
    }

    @Test
    public void candidateFilterBindsOneSelectedGramAndLeavesExactCheckToCaller() {
        InsuranceNameNgramIndex index = new InsuranceNameNgramIndex(null);
        index.setReadEnabled(true);
        QueryWrapper<Insurance> query = new QueryWrapper<>();

        byte[] gram = "体团险".getBytes(StandardCharsets.UTF_8);
        index.appendCandidateFilter(query, gram);

        String sql = query.getSqlSegment();
        assertTrue(sql, sql.contains("tab_insurance.id IN"));
        assertTrue(sql, sql.contains("candidate.ngram"));
        assertEquals(1, query.getParamNameValuePairs().size());
        assertTrue(query.getParamNameValuePairs().containsValue(gram));
    }

    @Test
    public void readFlagDefaultsToFallbackAndDoesNotAddIndexPredicate() {
        InsuranceNameNgramIndex index = new InsuranceNameNgramIndex(null);
        QueryWrapper<Insurance> query = new QueryWrapper<>();

        index.appendCandidateFilter(query, "团体险");

        assertEquals("", query.getSqlSegment());
    }

    @Test
    public void rejectsReadEnabledWhenWriteMaintenanceIsDisabled() {
        InsuranceNameNgramIndex index = new InsuranceNameNgramIndex(null);
        index.setReadEnabled(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, index::validateConfiguration);

        assertTrue(exception.getMessage().contains("write-enabled=true"));
    }

    @Test
    public void acceptsReadAndWriteEnabledTogether() {
        InsuranceNameNgramIndex index = new InsuranceNameNgramIndex(null);
        index.setWriteEnabled(true);
        index.setReadEnabled(true);

        index.validateConfiguration();
    }
}

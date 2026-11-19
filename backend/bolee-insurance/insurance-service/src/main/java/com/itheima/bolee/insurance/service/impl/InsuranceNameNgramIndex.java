package com.itheima.bolee.insurance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itheima.bolee.insurance.mapper.InsuranceNameNgramMapper;
import com.itheima.bolee.insurance.mapper.InsuranceNameNgramMapper.NgramValue;
import com.itheima.bolee.insurance.pojo.Insurance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains and queries a trigram candidate index while retaining SQL LIKE as the exact check.
 * Reads and writes are separately gated so the index can be backfilled before query cutover.
 */
@Component
public class InsuranceNameNgramIndex {

    private static final int MAX_NAME_CODE_POINTS = 64;

    private final InsuranceNameNgramMapper mapper;

    @Value("${insurance.search.name-ngram.write-enabled:false}")
    private boolean writeEnabled;

    @Value("${insurance.search.name-ngram.read-enabled:false}")
    private boolean readEnabled;

    public InsuranceNameNgramIndex(InsuranceNameNgramMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    void validateConfiguration() {
        if (readEnabled && !writeEnabled) {
            throw new IllegalStateException("insurance.search.name-ngram.read-enabled requires " +
                "insurance.search.name-ngram.write-enabled=true so product changes cannot make the index stale");
        }
    }

    public void replace(Long insuranceId, String insuranceName) {
        if (!writeEnabled || insuranceId == null) {
            return;
        }
        mapper.decrementStatsByInsuranceId(insuranceId);
        mapper.deleteByInsuranceId(insuranceId);
        mapper.deleteEmptyStats();
        List<byte[]> ngrams = trigrams(insuranceName);
        if (!ngrams.isEmpty()) {
            mapper.insertNgrams(insuranceId, ngrams);
            mapper.incrementStats(ngrams);
        }
    }

    public void delete(List<Long> insuranceIds) {
        if (writeEnabled && insuranceIds != null && !insuranceIds.isEmpty()) {
            mapper.decrementStatsByInsuranceIds(insuranceIds);
            mapper.deleteByInsuranceIds(insuranceIds);
            mapper.deleteEmptyStats();
        }
    }

    public void appendCandidateFilter(QueryWrapper<Insurance> queryWrapper, String keyword) {
        if (!readEnabled || !isIndexable(keyword)) {
            return;
        }
        List<byte[]> grams = trigrams(keyword);
        if (grams.isEmpty()) {
            return;
        }
        NgramValue rarestValue = mapper.selectRarestNgram(grams);
        byte[] rarestGram = rarestValue == null ? null : rarestValue.getNgram();
        appendCandidateFilter(queryWrapper, rarestGram);
    }

    void appendCandidateFilter(QueryWrapper<Insurance> queryWrapper, byte[] rarestGram) {
        if (!readEnabled || rarestGram == null || rarestGram.length == 0) {
            return;
        }
        // Any literal match contains this trigram. The original LIKE remains the exact check.
        queryWrapper.apply("tab_insurance.id IN (" +
            "SELECT candidate.insurance_id FROM tab_insurance_name_ngram candidate " +
            "WHERE candidate.ngram = {0})", rarestGram);
    }

    static boolean isIndexable(String keyword) {
        if (keyword == null || keyword.indexOf('%') >= 0 || keyword.indexOf('_') >= 0 ||
            keyword.indexOf('\\') >= 0) {
            return false;
        }
        int codePointCount = keyword.codePointCount(0, keyword.length());
        if (codePointCount < 3 || codePointCount > MAX_NAME_CODE_POINTS) {
            return false;
        }
        // The legacy LIKE column uses a case/accent-insensitive MySQL collation,
        // while postings compare exact UTF-8 bytes. Restrict candidate filtering
        // to common BMP Han characters so the index cannot exclude collation-equal
        // ASCII/Latin matches that the legacy LIKE query would return.
        return keyword.codePoints().allMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    static List<byte[]> trigrams(String value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }
        int[] codePoints = value.codePoints().toArray();
        Map<String, byte[]> distinct = new LinkedHashMap<>();
        for (int index = 0; index + 2 < codePoints.length; index++) {
            String gram = new String(codePoints, index, 3);
            byte[] bytes = gram.getBytes(StandardCharsets.UTF_8);
            distinct.putIfAbsent(Base64.getEncoder().encodeToString(bytes), bytes);
        }
        return new ArrayList<>(distinct.values());
    }

    void setReadEnabled(boolean readEnabled) {
        this.readEnabled = readEnabled;
    }

    void setWriteEnabled(boolean writeEnabled) {
        this.writeEnabled = writeEnabled;
    }
}

package com.itheima.bolee.insurance.service.ai;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Small deterministic name matcher used only to surface confirmation candidates. */
public final class ProductNameSimilarity {
    private static final Set<String> COMMON_BIGRAMS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList("保险", "险产", "产品", "保障", "计划", "方案", "投保")));

    private ProductNameSimilarity() { }

    public static Set<String> bigrams(String value) {
        if (value == null) return Collections.emptySet();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 4) return Collections.emptySet();
        Set<String> result = new LinkedHashSet<String>();
        for (int index = 0; index + 1 < normalized.length(); index++) {
            String gram = normalized.substring(index, index + 2);
            if (!gram.trim().isEmpty() && !COMMON_BIGRAMS.contains(gram)) result.add(gram);
        }
        return result;
    }

    public static int minimumMatches(Collection<String> grams) {
        if (grams == null || grams.size() < 2) return Integer.MAX_VALUE;
        return Math.max(2, (int) Math.ceil(grams.size() * 0.5d));
    }

    public static int sharedCount(String query, String candidate) {
        Set<String> queryGrams = bigrams(query);
        if (queryGrams.isEmpty()) return 0;
        Set<String> candidateGrams = bigrams(candidate);
        int count = 0;
        for (String gram : queryGrams) if (candidateGrams.contains(gram)) count++;
        return count;
    }

    public static boolean isSimilar(String query, String candidate) {
        int shared = sharedCount(query, candidate);
        return shared >= minimumMatches(bigrams(query));
    }
}

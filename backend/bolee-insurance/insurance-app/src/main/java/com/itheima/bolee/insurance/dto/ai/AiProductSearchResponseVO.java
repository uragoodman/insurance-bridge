package com.itheima.bolee.insurance.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Normal business result for the initial category-based catalog query. */
@Data
@AllArgsConstructor
public class AiProductSearchResponseVO {
    private String status;
    private List<String> missingCriteria;
    private List<AiProductCandidateVO> candidates;
}

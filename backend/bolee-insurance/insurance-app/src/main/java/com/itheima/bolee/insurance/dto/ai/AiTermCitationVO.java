package com.itheima.bolee.insurance.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Customer-facing source citation for an insurance terms answer. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTermCitationVO {
    private String citationId;
    private String sourceId;
    private String sourceTitle;
    private String productId;
    private Integer productVersion;
    private String profileVersionId;
    private String clauseId;
    private String clauseTitle;
    private Integer pageStart;
    private Integer pageEnd;
    private String excerpt;
}

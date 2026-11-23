package com.itheima.bolee.insurance.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Citation payload produced by the Python terms retrieval service. */
@Data
@NoArgsConstructor
public class AgnoTermCitationDTO {
    @JsonProperty("citation_id")
    private String citationId;
    @JsonProperty("source_id")
    private String sourceId;
    @JsonProperty("source_title")
    private String sourceTitle;
    @JsonProperty("product_id")
    private String productId;
    @JsonProperty("product_version")
    private Integer productVersion;
    @JsonProperty("profile_version_id")
    private String profileVersionId;
    @JsonProperty("clause_id")
    private String clauseId;
    @JsonProperty("clause_title")
    private String clauseTitle;
    @JsonProperty("page_start")
    private Integer pageStart;
    @JsonProperty("page_end")
    private Integer pageEnd;
    private String excerpt;
}

package com.itheima.bolee.insurance.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Internal snake_case comparison fact received from the Python Agent service. */
@Data
@NoArgsConstructor
public class AgnoRecommendationFactDTO {
    private String code;

    @JsonProperty("definition_version")
    private Integer definitionVersion;

    private String name;

    @JsonProperty("value_type")
    private String valueType;

    private String unit;

    @JsonProperty("value_code")
    private String valueCode;

    @JsonProperty("value_codes")
    private List<String> valueCodes = new ArrayList<String>();

    @JsonProperty("value_display")
    private String valueDisplay;

    @JsonProperty("value_number")
    private BigDecimal valueNumber;

    @JsonProperty("min_value")
    private BigDecimal minValue;

    @JsonProperty("max_value")
    private BigDecimal maxValue;

    @JsonProperty("value_status")
    private String valueStatus;
}

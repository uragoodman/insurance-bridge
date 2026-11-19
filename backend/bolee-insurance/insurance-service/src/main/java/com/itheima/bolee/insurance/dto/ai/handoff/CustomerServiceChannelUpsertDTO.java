package com.itheima.bolee.insurance.dto.ai.handoff;

import lombok.Data;


/** Management-only channel input. targetValue is kept out of customer response objects. */
@Data
public class CustomerServiceChannelUpsertDTO {
    private String channelCode;
    private String channelType;
    private String displayName;
    private String targetValue;
    private String serviceHoursDesc;
    private String timezoneId;
    private String status;
    private Integer sortNo;
}

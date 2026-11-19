package com.itheima.bolee.insurance.dto.ai.handoff;

import lombok.Data;

@Data
public class CustomerServiceChannelAdminVO {
    private Long id;
    private String channelCode;
    private String channelType;
    private String displayName;
    private String targetValue;
    private String serviceHoursDesc;
    private String timezoneId;
    private String status;
    private Integer sortNo;
}

package com.itheima.bolee.insurance.dto.ai.handoff;

import lombok.Data;

@Data
public class CustomerServiceChannelVO {
    private Long id;
    private String channelCode;
    private String channelType;
    private String displayName;
    private String serviceHoursDesc;
    private String timezoneId;
}

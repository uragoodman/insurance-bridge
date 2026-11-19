package com.itheima.bolee.insurance.pojo.ai;

import com.itheima.bolee.framework.mybatisplus.basic.BasePojo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomerServiceChannelDO extends BasePojo {
    private String companyNo;
    private String channelCode;
    private String channelType;
    private String displayName;
    private String targetValue;
    private String serviceHoursDesc;
    private String timezoneId;
    private String status;
    private Integer sortNo;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
}

package com.itheima.bolee.insurance.pojo.ai.terms;

import com.baomidou.mybatisplus.annotation.TableName;
import com.itheima.bolee.framework.mybatisplus.basic.BasePojo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("tab_insurance_term_source")
public class InsuranceTermSourceDO extends BasePojo {

    private Long insuranceId;
    private Long profileVersionId;
    private Integer productVersion;
    private String sourceType;
    private String title;
    private String sourceUri;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private String storageProvider;
    private String bucketName;
    private String objectKey;
    private String status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Long uploadedBy;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Long withdrawnBy;
    private LocalDateTime withdrawnAt;
    private String withdrawalReason;
    private String indexStatus;
    private String indexedSha256;
    private String indexError;
}

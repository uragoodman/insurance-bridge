package com.itheima.bolee.insurance.dto.ai.terms;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class InsuranceTermIndexTaskVO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long taskId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long sourceId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long productId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long profileVersionId;
    private Integer productVersion;
    private String sourceType;
    private String action;
    private String sourceSha256;
    private String title;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String contentPath;
    private String leaseToken;
    private Integer leaseDurationSeconds;
    private LocalDateTime leaseUntil;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getProfileVersionId() { return profileVersionId; }
    public void setProfileVersionId(Long profileVersionId) { this.profileVersionId = profileVersionId; }
    public Integer getProductVersion() { return productVersion; }
    public void setProductVersion(Integer productVersion) { this.productVersion = productVersion; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getSourceSha256() { return sourceSha256; }
    public void setSourceSha256(String sourceSha256) { this.sourceSha256 = sourceSha256; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getContentPath() { return contentPath; }
    public void setContentPath(String contentPath) { this.contentPath = contentPath; }
    public String getLeaseToken() { return leaseToken; }
    public void setLeaseToken(String leaseToken) { this.leaseToken = leaseToken; }
    public Integer getLeaseDurationSeconds() { return leaseDurationSeconds; }
    public void setLeaseDurationSeconds(Integer leaseDurationSeconds) { this.leaseDurationSeconds = leaseDurationSeconds; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime leaseUntil) { this.leaseUntil = leaseUntil; }
}

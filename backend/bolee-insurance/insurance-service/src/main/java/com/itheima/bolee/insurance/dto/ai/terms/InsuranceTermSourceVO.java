package com.itheima.bolee.insurance.dto.ai.terms;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class InsuranceTermSourceVO {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long insuranceId;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long profileVersionId;
    private Integer productVersion;
    private String sourceType;
    private String title;
    private String sourceUri;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private String status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private LocalDateTime createTime;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long uploadedBy;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long submittedBy;
    private LocalDateTime submittedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNote;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long publishedBy;
    private LocalDateTime publishedAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long withdrawnBy;
    private LocalDateTime withdrawnAt;
    private String withdrawalReason;
    private String indexStatus;
    private String indexedSha256;
    private String indexError;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInsuranceId() { return insuranceId; }
    public void setInsuranceId(Long insuranceId) { this.insuranceId = insuranceId; }
    public Long getProfileVersionId() { return profileVersionId; }
    public void setProfileVersionId(Long profileVersionId) { this.profileVersionId = profileVersionId; }
    public Integer getProductVersion() { return productVersion; }
    public void setProductVersion(Integer productVersion) { this.productVersion = productVersion; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceUri() { return sourceUri; }
    public void setSourceUri(String sourceUri) { this.sourceUri = sourceUri; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }
    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public Long getPublishedBy() { return publishedBy; }
    public void setPublishedBy(Long publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public Long getWithdrawnBy() { return withdrawnBy; }
    public void setWithdrawnBy(Long withdrawnBy) { this.withdrawnBy = withdrawnBy; }
    public LocalDateTime getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(LocalDateTime withdrawnAt) { this.withdrawnAt = withdrawnAt; }
    public String getWithdrawalReason() { return withdrawalReason; }
    public void setWithdrawalReason(String withdrawalReason) { this.withdrawalReason = withdrawalReason; }
    public String getIndexStatus() { return indexStatus; }
    public void setIndexStatus(String indexStatus) { this.indexStatus = indexStatus; }
    public String getIndexedSha256() { return indexedSha256; }
    public void setIndexedSha256(String indexedSha256) { this.indexedSha256 = indexedSha256; }
    public String getIndexError() { return indexError; }
    public void setIndexError(String indexError) { this.indexError = indexError; }
}

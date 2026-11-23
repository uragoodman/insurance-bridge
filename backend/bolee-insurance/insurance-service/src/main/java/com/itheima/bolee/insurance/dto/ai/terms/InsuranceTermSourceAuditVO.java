package com.itheima.bolee.insurance.dto.ai.terms;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class InsuranceTermSourceAuditVO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    private String action;
    private String fromStatus;
    private String toStatus;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long actorId;
    private String note;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}

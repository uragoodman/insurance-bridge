package com.itheima.bolee.insurance.dto.ai.terms;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class InsuranceTermSourceReviewRequest {
    @NotNull
    private Boolean approved;
    @Size(max = 1000)
    private String note;

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

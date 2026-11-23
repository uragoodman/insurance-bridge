package com.itheima.bolee.insurance.dto.ai.terms;

import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDate;

public class InsuranceTermSourceUpdateRequest {
    @NotBlank
    @Size(max = 255)
    private String title;
    @Size(max = 1024)
    private String sourceUri;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate effectiveTo;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceUri() { return sourceUri; }
    public void setSourceUri(String sourceUri) { this.sourceUri = sourceUri; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
}

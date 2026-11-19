package com.itheima.bolee.insurance.config.ai.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/** Required retention windows for AI-owned data; deliberately has no implicit defaults. */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.retention")
public class AiRetentionProperties {

    @NotNull
    @Min(1)
    private Integer conversationDays;

    @NotNull
    @Min(1)
    private Integer traceDays;

    @NotNull
    @Min(1)
    private Integer cleanupTaskDays;

    public LocalDateTime conversationExpiresAt(LocalDateTime lastActivityAt) {
        return lastActivityAt.plusDays(conversationDays);
    }

    public LocalDateTime traceExpiresAt(LocalDateTime completedAt) {
        return completedAt.plusDays(traceDays);
    }

    public LocalDateTime cleanupTaskExpiresAt(LocalDateTime completedAt) {
        return completedAt.plusDays(cleanupTaskDays);
    }
}

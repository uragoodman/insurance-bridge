package com.itheima.bolee.insurance.pojo.ai;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** Lifecycle of a durable Java-owned request to remove Agno session data. */
public enum AiCleanupTaskStatus {
    PENDING("PENDING"),
    PROCESSING("PROCESSING"),
    RETRY_WAIT("RETRY_WAIT"),
    COMPLETED("COMPLETED"),
    DEAD_LETTER("DEAD_LETTER");

    @EnumValue
    private final String value;

    AiCleanupTaskStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

package com.itheima.bolee.insurance.pojo.ai;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** Persisted lifecycle values for one AI chat turn. */
public enum AiTurnStatus {
    PROCESSING("PROCESSING"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED");

    @EnumValue
    private final String value;

    AiTurnStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

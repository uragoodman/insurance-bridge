package com.itheima.bolee.insurance.pojo.ai;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** Persisted lifecycle values for Java-owned AI conversation metadata. */
public enum AiConversationStatus {
    ACTIVE("ACTIVE"),
    CLOSED("CLOSED"),
    DELETE_PENDING("DELETE_PENDING"),
    DELETED("DELETED");

    @EnumValue
    private final String value;

    AiConversationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}

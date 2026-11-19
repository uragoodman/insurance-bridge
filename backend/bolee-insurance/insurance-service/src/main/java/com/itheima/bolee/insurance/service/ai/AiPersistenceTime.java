package com.itheima.bolee.insurance.service.ai;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** MySQL AI tables use DATETIME(3), so all persisted timestamps share millisecond precision. */
public final class AiPersistenceTime {
    private AiPersistenceTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }
}

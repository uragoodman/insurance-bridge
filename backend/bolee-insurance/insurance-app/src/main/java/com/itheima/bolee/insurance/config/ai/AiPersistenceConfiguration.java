package com.itheima.bolee.insurance.config.ai;

import com.itheima.bolee.insurance.config.ai.properties.AiRetentionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables validated retention settings only when AI metadata persistence is explicitly enabled. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "ai.persistence", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiRetentionProperties.class)
public class AiPersistenceConfiguration {
}

package com.itheima.bolee.insurance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the Java-side retention job for customer-service requests. */
@Configuration
@EnableScheduling
public class CustomerServiceSchedulingConfiguration {
}

package com.dilaw.mohe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StudentDataConfig {

    /**
     * Registers the {@link SensitiveFieldModule} with Spring Boot's auto-configured
     * Jackson {@code ObjectMapper}. Any field annotated with {@link SensitiveField}
     * will be redacted to {@code null} in JSON responses unless the caller holds at
     * least one of the listed authority codes.
     */
    @Bean
    public SensitiveFieldModule sensitiveFieldModule() {
        return new SensitiveFieldModule();
    }
}

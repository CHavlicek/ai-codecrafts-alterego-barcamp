package com.aiavatar.alterego.infrastructure.config;

import com.aiavatar.alterego.domain.policy.RandomCategorySelector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires pure domain helpers into the Spring container so they can be
 * injected into application use cases without polluting the domain layer
 * with Spring annotations (Constitution Principle VII — domain purity).
 */
@Configuration
public class DomainBeansConfig {

    @Bean
    public RandomCategorySelector randomCategorySelector() {
        return new RandomCategorySelector();
    }
}

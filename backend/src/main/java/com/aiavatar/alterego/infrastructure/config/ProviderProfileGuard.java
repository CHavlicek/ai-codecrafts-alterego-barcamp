package com.aiavatar.alterego.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Refuse startup when both the {@code gemini} and {@code falai} real-provider
 * profiles are active simultaneously (016 FR-1605 / clarification Q1).
 *
 * <p>The two real providers are deliberately mutually exclusive: a single
 * Generate request must route through exactly one of them. Activating both
 * would leave the {@code @Primary} bean wiring non-deterministic across
 * Spring versions and would silently surprise an operator who thought they
 * had switched provider when in fact the wrong one is still serving traffic.
 *
 * <p>The check runs once at context-init time via {@link PostConstruct};
 * Spring Boot's startup wraps the {@link IllegalStateException} into an
 * {@code ApplicationContextException} and the JVM exits non-zero. The
 * orchestrator never gets a chance to serve a request.
 */
@Configuration
public class ProviderProfileGuard {

    private static final Logger log = LoggerFactory.getLogger(ProviderProfileGuard.class);
    private static final String GEMINI = "gemini";
    private static final String FALAI = "falai";

    private final Environment env;

    public ProviderProfileGuard(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void guardAgainstAmbiguousProvider() {
        Set<String> active = new LinkedHashSet<>(Arrays.asList(env.getActiveProfiles()));
        if (active.contains(GEMINI) && active.contains(FALAI)) {
            String message = "Refusing to start: both 'gemini' and 'falai' real-provider "
                    + "profiles are active simultaneously. Set spring.profiles.active to "
                    + "EXACTLY ONE of {gemini, falai} (or neither for the stub-only path). "
                    + "Active profiles: " + active;
            log.error("event=startup.fatal reason=ambiguous_provider profiles={}", active);
            throw new IllegalStateException(message);
        }
    }
}

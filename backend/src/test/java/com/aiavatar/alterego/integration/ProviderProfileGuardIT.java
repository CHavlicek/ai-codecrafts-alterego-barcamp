package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.AlterEgoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 016 T038 — full-stack proof that activating both {@code gemini} AND
 * {@code falai} simultaneously refuses startup (FR-1605).
 *
 * <p>Boots the {@link AlterEgoApplication} in {@link WebApplicationType#NONE}
 * mode (no Tomcat) under both profiles and asserts the
 * {@link BeanCreationException} that bubbles out of
 * {@code SpringApplication.run} carries the {@link IllegalStateException}
 * thrown by {@code ProviderProfileGuard.@PostConstruct}.
 *
 * <p>Lives in {@code integration/} to mirror the test layout convention,
 * but doesn't use {@code @SpringBootTest} because its goal is to assert
 * that the context fails to refresh — which JUnit's container-managed
 * Spring context doesn't expose cleanly.
 */
class ProviderProfileGuardIT {

    @Test
    void contextRefuseStartupWhenBothRealProviderProfilesAreActive() {
        SpringApplication app = new SpringApplication(AlterEgoApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles("gemini", "falai");

        // Pin a blank API key so the Gemini path doesn't try to outbound.
        // (The guard fires at @PostConstruct, before any controller wiring,
        // so this is belt-and-braces — but it keeps the test deterministic
        // even if a future change reorders init.)
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ctx = app.run(
                    "--aiavatar.gemini.api-key=",
                    "--aiavatar.falai.api-key=")) {
                // If we somehow reach here the guard didn't fire — fail loud.
                throw new AssertionError(
                        "Spring context refresh should have failed under "
                                + "spring.profiles.active=gemini,falai");
            }
        })
                // Spring Boot wraps the @PostConstruct IllegalStateException
                // into a BeanCreationException at startup. The root cause is
                // the IllegalStateException ProviderProfileGuard throws.
                .isInstanceOf(BeanCreationException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .satisfies(t -> {
                    assertThat(t.getMessage()).contains("gemini");
                    assertThat(t.getMessage()).contains("falai");
                    assertThat(t.getMessage()).contains("Refusing to start");
                });
    }
}

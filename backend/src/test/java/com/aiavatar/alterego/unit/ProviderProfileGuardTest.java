package com.aiavatar.alterego.unit;

import com.aiavatar.alterego.infrastructure.config.ProviderProfileGuard;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 016 T037 — unit coverage for {@link ProviderProfileGuard} (FR-1605).
 *
 * <ul>
 *   <li>Both {@code gemini} AND {@code falai} active → throws
 *       {@link IllegalStateException} naming both profiles.</li>
 *   <li>Either alone → no throw.</li>
 *   <li>Neither active (default profile) → no throw.</li>
 * </ul>
 */
class ProviderProfileGuardTest {

    @Test
    void bothRealProviderProfilesActiveRefusesStartup() {
        Environment env = new MockEnvironment().withProperty("ignored", "x");
        ((MockEnvironment) env).setActiveProfiles("gemini", "falai");
        ProviderProfileGuard guard = new ProviderProfileGuard(env);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                guard::guardAgainstAmbiguousProvider);
        assertTrue(ex.getMessage().contains("gemini"),
                "fatal message MUST name the gemini profile");
        assertTrue(ex.getMessage().contains("falai"),
                "fatal message MUST name the falai profile");
    }

    @Test
    void orderOfActivationDoesNotChangeRefusal() {
        // Listing 'falai' before 'gemini' MUST also refuse — no precedence,
        // no alphabetical tiebreaker (clarification Q1 → A: refuse to start).
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("falai", "gemini");
        ProviderProfileGuard guard = new ProviderProfileGuard(env);

        assertThrows(IllegalStateException.class, guard::guardAgainstAmbiguousProvider);
    }

    @Test
    void onlyGeminiActiveDoesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("gemini");
        ProviderProfileGuard guard = new ProviderProfileGuard(env);
        assertDoesNotThrow(guard::guardAgainstAmbiguousProvider);
    }

    @Test
    void onlyFalaiActiveDoesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("falai");
        ProviderProfileGuard guard = new ProviderProfileGuard(env);
        assertDoesNotThrow(guard::guardAgainstAmbiguousProvider);
    }

    @Test
    void defaultProfileDoesNotThrow() {
        // No explicit setActiveProfiles → MockEnvironment reports zero
        // active profiles, which is the production "default profile" case
        // (FR-1603: stub-only path).
        ProviderProfileGuard guard = new ProviderProfileGuard(new MockEnvironment());
        assertDoesNotThrow(guard::guardAgainstAmbiguousProvider);
    }

    @Test
    void unrelatedProfilesDoNotInterfere() {
        // Profiles like `force-stub-failure` (used by 001 SC-004 IT) coexist
        // with the gemini path without tripping the guard.
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("gemini", "force-stub-failure");
        ProviderProfileGuard guard = new ProviderProfileGuard(env);
        assertDoesNotThrow(guard::guardAgainstAmbiguousProvider);
    }
}

package com.aiavatar.alterego.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Resilient HTTP retry policy per constitutional Principle IV:
 * 5 attempts, exponential back-off, randomised jitter.
 * <p>
 * The stub generators in this POC don't make HTTP calls so the template is
 * effectively dormant — but it's wired so the integration tests can prove
 * the retry path works, and so the follow-up feature that swaps real
 * providers in just plugs into an existing bean.
 */
@Configuration
public class RetryConfig {

    public static final int MAX_ATTEMPTS = 5;
    public static final long INITIAL_BACKOFF_MS = 200;
    public static final double BACKOFF_MULTIPLIER = 2.0;
    public static final long MAX_BACKOFF_MS = 5_000;

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate template = new RetryTemplate();

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(MAX_ATTEMPTS);
        template.setRetryPolicy(retryPolicy);

        // ExponentialRandomBackOffPolicy applies a random jitter on top of the
        // exponential schedule, satisfying the "randomised jitter" half of
        // Principle IV without hand-rolling our own jitter.
        ExponentialRandomBackOffPolicy backOff = new ExponentialRandomBackOffPolicy();
        backOff.setInitialInterval(INITIAL_BACKOFF_MS);
        backOff.setMultiplier(BACKOFF_MULTIPLIER);
        backOff.setMaxInterval(MAX_BACKOFF_MS);
        template.setBackOffPolicy(backOff);

        return template;
    }
}

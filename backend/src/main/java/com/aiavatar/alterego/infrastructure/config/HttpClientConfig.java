package com.aiavatar.alterego.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * Provides the shared {@link HttpClient} and {@link Clock} beans the
 * provider clients depend on. Both are stateless and thread-safe.
 *
 * <ul>
 *   <li>{@link HttpClient} — used by 003's {@code GeminiClient} and 016's
 *       {@code FalAiClient}. One pooled instance; per-request timeouts are
 *       applied by the caller via {@code HttpRequest.Builder#timeout}.</li>
 *   <li>{@link Clock} — used by 016's {@code FalAiClient} for the FR-1614a
 *       end-to-end deadline check. Defaults to {@code Clock.systemUTC()};
 *       tests inject a fixed clock to force the {@code TIMEOUT} branch
 *       deterministically.</li>
 * </ul>
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient() {
        // 016: in-memory cookie jar so subsequent requests echo back any
        // bot-management cookies (e.g. Cloudflare's `__cf_bm`) issued on the
        // first contact. fal.ai's edge sits behind Cloudflare and treats
        // cookieless clients as adversarial — first request usually slips
        // through, second+ get 403'd. The cookie store is shared across the
        // whole JVM and lives only in process memory; no persistence layer
        // is touched (FR-016 / FR-1618).
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder()
                // Connect-timeout: bounded short so a dead DNS / routing issue
                // surfaces as a NETWORK_ERROR rather than waiting on the
                // per-request timeout.
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(cookieManager)
                .build();
    }

    /**
     * Production {@link Clock}. 016's {@code FalAiClient} uses it to capture
     * a wall-clock deadline at the start of each Generate request and to
     * check whether the budget remains before each outbound HTTP step.
     */
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}

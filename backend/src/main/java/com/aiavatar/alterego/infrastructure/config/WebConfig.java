package com.aiavatar.alterego.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for the local dev profile only.
 * <p>
 * In Docker Compose and production, the frontend Nginx proxies {@code /api/**}
 * to the backend service so requests arrive same-origin from the browser's
 * perspective. The Vite dev proxy does the same for {@code npm run dev}, but
 * a developer hitting the backend directly from a tool (curl / a browser tab
 * pointed at {@code :8080}) benefits from explicit dev-only CORS.
 * <p>
 * The {@code default} profile is the dev profile per
 * {@code application.yml:spring.profiles.active}.
 */
@Configuration
@Profile("default")
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Request-Id")
                .exposedHeaders("X-Request-Id")
                .allowCredentials(false)
                .maxAge(3600);
    }
}

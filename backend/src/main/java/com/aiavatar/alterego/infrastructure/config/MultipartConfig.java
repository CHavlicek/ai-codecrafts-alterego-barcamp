package com.aiavatar.alterego.infrastructure.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Programmatic multipart limits. The same caps live in {@code application.yml}
 * for visibility, but defining the bean here makes the policy explicit in code
 * and survives any yaml refactor.
 * <p>
 * 20 MB per file, 21 MB per request — raised from 5/6 MB in 003 so that
 * phone-sized photos reach the backend {@code PhotoReducer} which brings them
 * under Gemini's per-request ceiling before the outbound call
 * (003 FR-206/FR-209, research.md R10).
 */
@Configuration
public class MultipartConfig {

    public static final DataSize MAX_FILE_SIZE = DataSize.ofMegabytes(20);
    public static final DataSize MAX_REQUEST_SIZE = DataSize.ofMegabytes(21);

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(MAX_FILE_SIZE);
        factory.setMaxRequestSize(MAX_REQUEST_SIZE);
        return factory.createMultipartConfig();
    }
}

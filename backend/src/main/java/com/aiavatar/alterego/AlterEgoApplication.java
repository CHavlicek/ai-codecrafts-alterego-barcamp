package com.aiavatar.alterego;

import com.aiavatar.alterego.infrastructure.config.EmailProperties;
import com.aiavatar.alterego.infrastructure.config.FalAiProperties;
import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
@EnableConfigurationProperties({
        GeminiProperties.class,
        FalAiProperties.class,
        EmailProperties.class
})
public class AlterEgoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlterEgoApplication.class, args);
    }
}

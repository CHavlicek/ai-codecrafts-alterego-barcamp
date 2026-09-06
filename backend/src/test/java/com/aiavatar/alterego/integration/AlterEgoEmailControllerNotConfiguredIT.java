package com.aiavatar.alterego.integration;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.mail.javamail.JavaMailSender;

/**
 * 023 → 027 — Ships-as ("not configured at startup") path.
 *
 * <p>The 027 four-input AND gate (FR-2706) now drives the not-configured
 * branch. With the new {@code application.yml} defaults, {@code spring.mail.host}
 * ships pointed at {@code mail.privateemail.com} — so the dominant
 * misconfiguration in the field is "host is set, but credentials + From
 * were forgotten". This test exercises exactly that shape: host
 * non-blank, username/password/From all blank.
 * {@link com.aiavatar.alterego.infrastructure.config.EmailConfigured#isConfigured()}
 * returns false and the controller MUST surface the typed 503 problem
 * detail without any SMTP attempt. Mirrors FR-2706 / FR-2708.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("default")
@TestPropertySource(properties = {
        "spring.mail.host=mail.privateemail.com",
        "spring.mail.username=",
        "spring.mail.password=",
        "aiavatar.email.from=",
})
class AlterEgoEmailControllerNotConfiguredIT {

    @Autowired private MockMvc mockMvc;

    // The bean may or may not exist depending on Spring Boot's
    // MailSenderAutoConfiguration — when host is blank, no JavaMailSender
    // is created. @MockitoBean(required=false) doesn't exist in current
    // Spring Boot, but providing one as a stub is fine (it will be a
    // no-op).
    @MockitoBean private JavaMailSender mailSender;

    private static final byte[] FAKE_PNG = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};

    @Test
    void unconfiguredReturns503WithTypedProblemDetailAndNeverAttemptsSmtp() throws Exception {
        mockMvc
                .perform(multipart("/api/v1/alter-egos/email")
                        .file(new MockMultipartFile("image", "poster.png", "image/png", FAKE_PNG))
                        .part(textPart("to", "someone@example.com"))
                        .part(textPart("firstName", "Dmytro")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type", endsWith("/email/not-configured")))
                .andExpect(jsonPath("$.status", equalTo(503)));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private static jakarta.servlet.http.Part textPart(String name, String value) {
        return com.aiavatar.alterego.testsupport.MockTextPart.of(name, value);
    }
}

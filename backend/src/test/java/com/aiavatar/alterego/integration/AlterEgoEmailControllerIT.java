package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.testsupport.MockTextPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Properties;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 023 (issue #57) — end-to-end (controller boundary) coverage of the
 * new {@code POST /api/v1/alter-egos/email} endpoint. Boots the real
 * Spring context with the four properties required by the 027 gate
 * (FR-2706 — host + username + password + From all non-blank) so
 * {@code EmailConfigured.isConfigured()} returns true; mocks
 * {@code JavaMailSender} at the bean boundary so no SMTP socket is opened.
 *
 * <p>The not-configured branch is exercised in
 * {@link AlterEgoEmailControllerNotConfiguredIT} (separate context to
 * avoid having to undo {@code @TestPropertySource}).
 *
 * <p>027 delta: the {@code @TestPropertySource} bundle was widened from a
 * single {@code spring.mail.host=smtp.test} (sufficient under the 023
 * host-only gate) to the four properties the 027 four-input AND-gate
 * requires. Without this widening, every assertion below would flip to
 * {@code 503 not-configured} once {@code EmailConfigured} starts inspecting
 * all four inputs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("default")
@TestPropertySource(properties = {
        "spring.mail.host=smtp.test",
        "spring.mail.username=tester",
        "spring.mail.password=pw",
        "aiavatar.email.from=tester@example.com",
})
class AlterEgoEmailControllerIT {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JavaMailSender mailSender;

    private static final byte[] FAKE_PNG = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47};

    @Test
    void happyPathReturns200AndDispatchesEmail() throws Exception {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        UUID correlationId = UUID.randomUUID();

        mockMvc
                .perform(multipart("/api/v1/alter-egos/email")
                        .file(new MockMultipartFile("image", "poster.png", "image/png", FAKE_PNG))
                        .part(textPart("to", "someone@example.com"))
                        .part(textPart("firstName", "Dmytro"))
                        .header("X-Request-Id", correlationId.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", correlationId.toString()))
                .andExpect(jsonPath("$.status").value("sent"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void malformedToReturns400ProblemDetail() throws Exception {
        mockMvc
                .perform(multipart("/api/v1/alter-egos/email")
                        .file(new MockMultipartFile("image", "poster.png", "image/png", FAKE_PNG))
                        .part(textPart("to", "not-an-email"))
                        .part(textPart("firstName", "Dmytro")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.errors[*].field", hasItem("to")));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void blankFirstNameReturns400ProblemDetail() throws Exception {
        mockMvc
                .perform(multipart("/api/v1/alter-egos/email")
                        .file(new MockMultipartFile("image", "poster.png", "image/png", FAKE_PNG))
                        .part(textPart("to", "someone@example.com"))
                        .part(textPart("firstName", "")))
                .andExpect(status().isBadRequest());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void unsupportedImageMimeReturns415() throws Exception {
        mockMvc
                .perform(multipart("/api/v1/alter-egos/email")
                        .file(new MockMultipartFile("image", "poster.gif", "image/gif", FAKE_PNG))
                        .part(textPart("to", "someone@example.com"))
                        .part(textPart("firstName", "Dmytro")))
                .andExpect(status().isUnsupportedMediaType());

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void smtpFailureBubblesUpAs502ProblemDetail() throws Exception {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        org.mockito.Mockito.doThrow(new MailSendException("transient"))
                .when(mailSender)
                .send(any(MimeMessage.class));

        mockMvc
                .perform(multipart("/api/v1/alter-egos/email")
                        .file(new MockMultipartFile("image", "poster.png", "image/png", FAKE_PNG))
                        .part(textPart("to", "someone@example.com"))
                        .part(textPart("firstName", "Dmytro")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type", endsWith("/email/send-failed")))
                .andExpect(jsonPath("$.status", equalTo(502)));
    }

    private static jakarta.servlet.http.Part textPart(String name, String value) {
        return MockTextPart.of(name, value);
    }
}

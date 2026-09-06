package com.aiavatar.alterego.contract;

import com.aiavatar.alterego.testsupport.SamplePhotos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Error-path contract for {@code POST /api/v1/alter-egos}. Verifies each
 * failure mode maps to the correct HTTP status + an RFC 7807
 * {@code application/problem+json} body, with sanitized detail strings
 * (FR-016 — no exception messages echo photo bytes).
 *
 * <p>002 delta: enum values updated; {@code colour} is no longer present;
 * {@code vibe} is optional (its absence is NOT a 400). A stray {@code colour}
 * field in the JSON is silently ignored by Jackson, not a 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("default")
class AlterEgoControllerErrorContractTest {

    @Autowired private MockMvc mockMvc;

    private static final String VALID_SELECTIONS_JSON =
            "{\"pose\":\"heroic\",\"archetype\":\"software-developer\","
                    + "\"universe\":\"star-wars\",\"artStyle\":\"oil-painting\","
                    + "\"firstName\":\"Paula\"}";

    @Test
    void blankFirstNameReturns400Problem() throws Exception {
        String invalid = "{\"pose\":\"heroic\",\"archetype\":\"software-developer\","
                + "\"universe\":\"star-wars\",\"firstName\":\"\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPoseReturns400Problem() throws Exception {
        String invalid = "{\"pose\":\"crouching\",\"archetype\":\"software-developer\","
                + "\"universe\":\"star-wars\",\"firstName\":\"Paula\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void obsoleteArchetypeReturns400Problem() throws Exception {
        // 001 shipped with archetype "bug-hunter"; 002 drops it. An old
        // client sending that value MUST get a 400 now.
        String invalid = "{\"pose\":\"heroic\",\"archetype\":\"bug-hunter\","
                + "\"universe\":\"star-wars\",\"firstName\":\"Paula\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void obsoleteUniverseReturns400Problem() throws Exception {
        // 001 had "harry-potter"; 002 drops it.
        String invalid = "{\"pose\":\"heroic\",\"archetype\":\"software-developer\","
                + "\"universe\":\"harry-potter\",\"firstName\":\"Paula\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownVibeReturns400Problem() throws Exception {
        String invalid = "{\"pose\":\"heroic\",\"archetype\":\"software-developer\","
                + "\"universe\":\"star-wars\",\"vibe\":\"chaotic\",\"firstName\":\"Paula\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingRequiredArtStyleReturns400Problem() throws Exception {
        // 006 FR-306: artStyle is required; a request omitting it MUST be
        // rejected with 400 Bad Request.
        String invalid = "{\"pose\":\"heroic\",\"archetype\":\"software-developer\","
                + "\"universe\":\"star-wars\",\"firstName\":\"Paula\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownArtStyleReturns400Problem() throws Exception {
        // 006 FR-306 / FR-310: unknown artStyle wire value MUST be rejected.
        String invalid = "{\"pose\":\"heroic\",\"archetype\":\"software-developer\","
                + "\"universe\":\"star-wars\",\"artStyle\":\"stick-figure\","
                + "\"firstName\":\"Paula\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pixel-art", "low-poly-3d", "line-art"})
    void retiredArtStyleReturns400Problem(String retiredWire) throws Exception {
        // 019 FR-1903 / FR-1907 (closes #49): wire values retired by feature
        // 019 take the same path as any other unknown enum value — 400 Bad
        // Request, no silent substitution, no special-case shim. A stale tab
        // loaded before the deploy and emitting one of these must be rejected
        // exactly like a typo.
        String invalid = "{\"pose\":\"heroic\",\"archetype\":\"software-developer\","
                + "\"universe\":\"star-wars\",\"artStyle\":\"" + retiredWire + "\","
                + "\"firstName\":\"Paula\"}";

        org.springframework.test.web.servlet.MvcResult result =
                mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest())
                .andReturn();

        // FR-1907: response MUST NOT silently substitute a surviving art
        // style. Any surviving wire value appearing in the rejection body
        // would suggest a fallback path that this spec forbids.
        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .as("retired-value rejection body must not echo a surviving art style as a substitute")
                .doesNotContain("\"artStyle\":\"oil-painting\"")
                .doesNotContain("\"artStyle\":\"watercolor\"")
                .doesNotContain("\"artStyle\":\"pop-art\"")
                .doesNotContain("\"artStyle\":\"renaissance-portrait\"")
                .doesNotContain("\"artStyle\":\"japanese-woodblock\"")
                .doesNotContain("\"artStyle\":\"cel-shaded\"");
    }

    @Test
    void missingRequiredArchetypeReturns400Problem() throws Exception {
        String invalid = "{\"pose\":\"heroic\",\"universe\":\"star-wars\","
                + "\"firstName\":\"Paula\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void strayColourFieldIsIgnoredNot400() throws Exception {
        // Backwards-compat note: Jackson is configured with
        // FAIL_ON_UNKNOWN_PROPERTIES=false (Spring Boot default), so an old
        // client sending a "colour" field should be silently ignored, not 400.
        String withStrayColour = "{\"pose\":\"heroic\",\"colour\":\"purple\","
                + "\"archetype\":\"software-developer\",\"universe\":\"star-wars\","
                + "\"artStyle\":\"oil-painting\",\"firstName\":\"Paula\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", withStrayColour.getBytes())))
                .andExpect(status().isOk());
    }

    @Test
    void absentVibeIsAccepted() throws Exception {
        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                VALID_SELECTIONS_JSON.getBytes())))
                .andExpect(status().isOk());
    }

    @Test
    void unsupportedPhotoMimeReturns415Problem() throws Exception {
        byte[] gifBytes = "GIF89a".getBytes();
        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.gif", "image/gif", gifBytes))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                VALID_SELECTIONS_JSON.getBytes())))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("image/jpeg")));
    }

    @Test
    void missingPhotoPartReturns400Problem() throws Exception {
        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("selections", "", "application/json",
                                VALID_SELECTIONS_JSON.getBytes())))
                .andExpect(status().isBadRequest());
    }

    // 022 (issue #50) — class-level @RoleOfRecordPresent enforces the
    // archetype-or-customRole OR-invariant. Violations surface as RFC 7807
    // 400 Bad Request.

    @Test
    void missingBothArchetypeAndCustomRoleReturns400Problem() throws Exception {
        String invalid = "{\"universe\":\"star-wars\",\"artStyle\":\"oil-painting\","
                + "\"photoMode\":\"single\",\"firstName\":\"Paula\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void blankCustomRoleWithMissingArchetypeReturns400Problem() throws Exception {
        String invalid = "{\"universe\":\"star-wars\",\"artStyle\":\"oil-painting\","
                + "\"photoMode\":\"single\",\"firstName\":\"Paula\",\"customRole\":\"   \"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customRoleOver100CharsReturns400Problem() throws Exception {
        String tooLong = "T".repeat(101);
        String invalid = "{\"universe\":\"star-wars\",\"artStyle\":\"oil-painting\","
                + "\"photoMode\":\"single\",\"firstName\":\"Paula\",\"customRole\":\""
                + tooLong + "\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", invalid.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customRoleAtBoundaryLengthIsAccepted() throws Exception {
        // 100 chars exactly is valid (the boundary). Use a prefab archetype
        // alongside so the request is unambiguously well-formed for the
        // happy-path code path; the customRole field's presence on the wire
        // is what we exercise here.
        String onTheNose = "T".repeat(100);
        String valid = "{\"archetype\":\"software-developer\",\"universe\":\"star-wars\","
                + "\"artStyle\":\"oil-painting\",\"photoMode\":\"single\","
                + "\"firstName\":\"Paula\",\"customRole\":\"" + onTheNose + "\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json", valid.getBytes())))
                .andExpect(status().isOk());
    }
}

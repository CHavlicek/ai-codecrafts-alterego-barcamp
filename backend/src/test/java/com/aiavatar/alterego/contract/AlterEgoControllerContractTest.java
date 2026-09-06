package com.aiavatar.alterego.contract;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.testsupport.OpenApiSpecPath;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T017 — Contract test for the happy path of {@code POST /api/v1/alter-egos}.
 * <ul>
 *   <li>200 status with the expected JSON shape (jsonPath assertions).</li>
 *   <li>Response body conforms to the OpenAPI 3.1 schema in
 *       {@code contracts/alter-egos.openapi.yaml}, validated via
 *       {@link OpenApiInteractionValidator} from
 *       {@code swagger-request-validator-mockmvc}.</li>
 *   <li>Server-generated {@code X-Request-Id} is echoed in the response header.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("default")
class AlterEgoControllerContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static OpenApiInteractionValidator validator;

    @BeforeAll
    static void initValidator() {
        validator = OpenApiInteractionValidator
                .createForSpecificationUrl(OpenApiSpecPath.specUrl())
                .build();
    }

    @Test
    void happyPathReturns200WithFullyPopulatedSuccessResponse() throws Exception {
        byte[] photoBytes = SamplePhotos.tinyJpeg();
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        String selectionsJson = objectMapper.writeValueAsString(selections);

        MvcResult result = mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", photoBytes))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                selectionsJson.getBytes())))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.character.heroTitleLine1").value("PAULA"))
                .andExpect(jsonPath("$.character.heroTitleLine2").exists())
                .andExpect(jsonPath("$.character.tagline").exists())
                .andExpect(jsonPath("$.character.superpowers.length()").value(3))
                .andExpect(jsonPath("$.character.quote").exists())
                .andExpect(jsonPath("$.poster.dataUrl").exists())
                .andExpect(jsonPath("$.poster.mediaType").value("image/png"))
                // 015: every poster is composited into the frame asset's
                // canvas (768×1152, 2:3 portrait) — FR-1507/SC-1503.
                .andExpect(jsonPath("$.poster.widthPx").value(768))
                .andExpect(jsonPath("$.poster.heightPx").value(1152))
                // 016 FR-1612: default profile reports outcome=fallback,
                // reason=not_configured, provider=stub. (Was outcome=real
                // pre-016.) User-visible flow is unchanged.
                .andExpect(jsonPath("$.meta.outcome").value("fallback"))
                .andExpect(jsonPath("$.meta.reason").value("not_configured"))
                .andExpect(jsonPath("$.meta.provider").value("stub"))
                .andExpect(jsonPath("$.meta.correlationId").exists())
                .andReturn();

        ValidationReport report = validator.validateResponse(
                "/api/v1/alter-egos",
                Request.Method.POST,
                SimpleResponse.Builder.ok()
                        .withContentType(MediaType.APPLICATION_JSON_VALUE)
                        .withBody(result.getResponse().getContentAsString())
                        .build());

        assertFalse(report.hasErrors(),
                () -> "OpenAPI response validation failed: " + report.getMessages());
    }

    @Test
    void groupPhotoModeIsAccepted() throws Exception {
        // 011 FR-1008 / FR-1009: explicit photoMode: "group" round-trips
        // successfully through the pipeline.
        byte[] photoBytes = SamplePhotos.tinyJpeg();
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "The Architects", PhotoMode.GROUP);
        String selectionsJson = objectMapper.writeValueAsString(selections);

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", photoBytes))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                selectionsJson.getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.outcome").value("fallback"))
                .andExpect(jsonPath("$.meta.provider").value("stub"));
    }

    @Test
    void photoModeAbsentIsAcceptedAsBackwardsCompatibility() throws Exception {
        // 011 FR-1009 / research.md R3 — old clients that predate the field
        // MUST continue to work. The JSON body intentionally omits photoMode.
        byte[] photoBytes = SamplePhotos.tinyJpeg();
        String selectionsJson = """
                {"pose":"heroic","archetype":"cloud-architect","universe":"star-wars",\
                "vibe":"rebel","artStyle":"oil-painting","firstName":"Paula"}""";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", photoBytes))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                selectionsJson.getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.outcome").value("fallback"))
                .andExpect(jsonPath("$.meta.provider").value("stub"));
    }

    @Test
    void unknownPhotoModeValueIsRejectedWithProblemDetails() throws Exception {
        // 011 Edge Cases / spec — unknown wire values are rejected per the
        // same contract as any other enum.
        byte[] photoBytes = SamplePhotos.tinyJpeg();
        String selectionsJson = """
                {"pose":"heroic","archetype":"cloud-architect","universe":"star-wars",\
                "vibe":"rebel","artStyle":"oil-painting","firstName":"Paula","photoMode":"team"}""";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", photoBytes))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                selectionsJson.getBytes())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestBodyWithoutPoseOrVibeProduces200() throws Exception {
        // 020 FR-2001 / FR-2002 / FR-2025: the new public DTO carries
        // archetype + universe + artStyle + firstName + photoMode only. The
        // server rolls pose / vibe internally per request.
        byte[] photoBytes = SamplePhotos.tinyJpeg();
        String selectionsJson = """
                {"archetype":"cloud-architect","universe":"star-wars",\
                "artStyle":"oil-painting","firstName":"Paula"}""";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", photoBytes))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                selectionsJson.getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.heroTitleLine1").value("PAULA"))
                .andExpect(jsonPath("$.meta.outcome").value("fallback"))
                .andExpect(jsonPath("$.meta.provider").value("stub"))
                .andExpect(jsonPath("$.meta.reason").value("not_configured"));
    }

    @Test
    void clientSuppliedPoseAndVibeAreIgnoredOnTheWire() throws Exception {
        // 020 FR-2025: any pose / vibe value supplied by the client MUST be
        // ignored. We verify the response is identical (same poster outcome,
        // same firstName echo) whether the JSON carries pose/vibe or not —
        // because the DTO doesn't have those record components, Jackson drops
        // the keys silently and the server rolls fresh.
        byte[] photoBytes = SamplePhotos.tinyJpeg();
        String withClientPoseVibe = """
                {"pose":"heroic","vibe":"rebel","archetype":"cloud-architect",\
                "universe":"star-wars","artStyle":"oil-painting","firstName":"Paula"}""";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "photo.jpg", "image/jpeg", photoBytes))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                withClientPoseVibe.getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.heroTitleLine1").value("PAULA"))
                .andExpect(jsonPath("$.meta.outcome").value("fallback"))
                .andExpect(jsonPath("$.meta.provider").value("stub"));
    }

    // 022 (issue #50) — happy-path contract for the customRole field.
    // (a) customRole-only (archetype omitted) → 200.
    // (b) The three new prefab archetype values are accepted → 200.

    @Test
    void customRoleWithoutArchetypeReturns200() throws Exception {
        String selectionsJson = "{\"universe\":\"star-wars\",\"artStyle\":\"oil-painting\","
                + "\"photoMode\":\"single\",\"firstName\":\"Paula\",\"customRole\":\"Tester\"}";

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                selectionsJson.getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.heroTitleLine1").value("PAULA"));
    }

    @Test
    void newPrefabArchetypeOptionsAreAccepted() throws Exception {
        // Spot-check each of the three 022 prefab additions. Default profile
        // routes through the fallback path; the assert is purely contract-
        // level (HTTP 200 + recognised body shape).
        for (String wire : new String[]{"hr", "administration", "customer-relations"}) {
            String selectionsJson = "{\"archetype\":\"" + wire + "\",\"universe\":\"star-wars\","
                    + "\"artStyle\":\"oil-painting\",\"photoMode\":\"single\","
                    + "\"firstName\":\"Paula\"}";
            mockMvc.perform(multipart("/api/v1/alter-egos")
                            .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg", SamplePhotos.tinyJpeg()))
                            .file(new MockMultipartFile("selections", "", "application/json",
                                    selectionsJson.getBytes())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.character.heroTitleLine1").value("PAULA"));
        }
    }

    @Test
    void pngPhotosAreAcceptedJustLikeJpegs() throws Exception {
        byte[] photoBytes = SamplePhotos.tinyPng();
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.STEALTHY, Archetype.AI_ENGINEER, Universe.CYBERPUNK, null, ArtStyle.CEL_SHADED, "Maria", null);
        String selectionsJson = objectMapper.writeValueAsString(selections);

        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "photo.png", "image/png", photoBytes))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                selectionsJson.getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.character.heroTitleLine1").value("MARIA"))
                // 016 FR-1612 — default profile reports outcome=fallback,
                // provider=stub. The reason field is now PRESENT (not_configured)
                // because every default-profile run is on the fallback path.
                .andExpect(jsonPath("$.meta.outcome").value("fallback"))
                .andExpect(jsonPath("$.meta.provider").value("stub"))
                .andExpect(jsonPath("$.meta.reason").value("not_configured"));
    }
}

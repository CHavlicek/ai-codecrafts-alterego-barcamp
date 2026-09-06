package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.testsupport.SamplePhotos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 011 / Issue #30 -- end-to-end (controller boundary) coverage of the
 * {@code firstName} input-validation rules.
 *
 * <p>For each rule family (length, ASCII control, Unicode invisible,
 * structural marker, instruction-shaped phrase), POSTs a multipart request
 * carrying that invalid value and asserts:
 *
 * <ul>
 *   <li>HTTP 400</li>
 *   <li>{@code Content-Type: application/problem+json}</li>
 *   <li>{@code errors[*].field} contains {@code firstName} and the matching
 *       {@code code} ({@code firstName.tooLong} /
 *       {@code firstName.invalidChars} /
 *       {@code firstName.looksLikeInstructions})</li>
 *   <li>The response body does NOT contain a per-test UUID nonce embedded
 *       in the rejected value (FR-1107 -- robust against false-positives:
 *       a UUID is overwhelmingly unlikely to appear by chance)</li>
 *   <li>The application log captured during the request does NOT contain
 *       the same UUID nonce (FR-1110)</li>
 * </ul>
 *
 * <p>The {@code AlterEgoService} is structurally insulated from rejected
 * requests because Spring's {@code @Valid} pipeline runs before the
 * controller method body -- a 400 status code is proof the service was
 * never invoked. We intentionally do <em>not</em> wrap the service in a
 * spy here: simpler test, fewer moving parts, no Mockito reset semantics
 * to reason about.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("default")
@ExtendWith(OutputCaptureExtension.class)
class AlterEgoControllerInputValidationIT {

    @Autowired private MockMvc mockMvc;

    private static final String VALID_TEMPLATE =
            "{\"pose\":\"heroic\",\"archetype\":\"cloud-architect\","
                    + "\"universe\":\"star-wars\",\"artStyle\":\"oil-painting\","
                    + "\"firstName\":%s}";

    /**
     * Each invalid fixture embeds a short per-test nonce. The no-echo /
     * no-log assertions then check that the nonce never appears in the
     * response body or captured log output -- a much stronger property
     * than checking for substrings like {@code "aaaa..."} that could
     * conceivably appear unrelated to the rejected value.
     *
     * <p>The nonce is 8 hex chars (4 bytes of randomness; collision risk
     * negligible) so that {@code prefix + nonce + suffix} stays well
     * under the 50-char Family-A length cap -- otherwise every
     * non-length test would actually trip Family A and the wrong
     * {@code errors[].code} would surface.
     */
    private record Fixture(String value, String nonce) {
        static Fixture wrap(String prefix, String suffix) {
            String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            return new Fixture(prefix + nonce + suffix, nonce);
        }
    }

    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7F) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private void assertRejection(Fixture fixture, String expectedCode, CapturedOutput output) throws Exception {
        String selections = String.format(VALID_TEMPLATE, jsonString(fixture.value()));
        MvcResult result = mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg",
                                SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                selections.getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("firstName")))
                .andExpect(jsonPath(
                        "$.errors[?(@.field == 'firstName')].code",
                        hasItem(equalTo(expectedCode))))
                .andReturn();

        String body = result.getResponse().getContentAsString();

        // FR-1107: response body never echoes the rejected value -- proven by
        // the per-test UUID nonce never appearing in the body.
        assertThat(body)
                .as("response body must not contain the rejected firstName UUID nonce")
                .doesNotContain(fixture.nonce());

        // FR-1110: rejected value must not appear in any log line.
        assertThat(output.getAll())
                .as("application log must not contain the rejected firstName UUID nonce")
                .doesNotContain(fixture.nonce());
    }

    // ---------------------------------------------------------------------
    // Family A -- length
    // ---------------------------------------------------------------------

    @Test
    void firstNameOver50CharsIsRejected(CapturedOutput output) throws Exception {
        // 8-char nonce + 43 'a's = 51 chars -- exactly over the boundary.
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Fixture f = new Fixture(nonce + "a".repeat(43), nonce);
        assertRejection(f, "firstName.tooLong", output);
    }

    // ---------------------------------------------------------------------
    // Family B -- ASCII control characters
    // ---------------------------------------------------------------------

    @Test
    void firstNameWithEmbeddedNewlineIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap("Pau\n", ""), "firstName.invalidChars", output);
    }

    @Test
    void firstNameWithEmbeddedNullByteIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap("Pau" + (char) 0x00, ""), "firstName.invalidChars", output);
    }

    @Test
    void firstNameWithDeleteCharIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap("Pau" + (char) 0x7F, ""), "firstName.invalidChars", output);
    }

    // ---------------------------------------------------------------------
    // Family C -- Unicode invisibles
    // ---------------------------------------------------------------------

    @Test
    void firstNameWithZeroWidthSpaceIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap("Pau" + (char) 0x200B, ""), "firstName.invalidChars", output);
    }

    @Test
    void firstNameWithRtlOverrideIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap("Pau" + (char) 0x202E, ""), "firstName.invalidChars", output);
    }

    @Test
    void firstNameWithBomIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap((char) 0xFEFF + "Pau", ""), "firstName.invalidChars", output);
    }

    // ---------------------------------------------------------------------
    // Family D -- structural injection markers
    // ---------------------------------------------------------------------

    @Test
    void firstNameWithAngleBracketIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap("Pa<", ""), "firstName.invalidChars", output);
    }

    @Test
    void firstNameWithBacktickIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap("Pa`", ""), "firstName.invalidChars", output);
    }

    @Test
    void firstNameWithTemplateInjectionBaitIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap("Pau${", "}"), "firstName.invalidChars", output);
    }

    // ---------------------------------------------------------------------
    // Family E -- instruction-shaped phrases
    // ---------------------------------------------------------------------

    @Test
    void firstNameWithIgnorePreviousIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap("Ignore previous ", ""),
                "firstName.looksLikeInstructions", output);
    }

    @Test
    void firstNameWithJailbreakIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap("Jailbreak ", ""),
                "firstName.looksLikeInstructions", output);
    }

    @Test
    void firstNameWithSystemColonIsRejected(CapturedOutput output) throws Exception {
        assertRejection(Fixture.wrap("system: ", ""),
                "firstName.looksLikeInstructions", output);
    }

    // ---------------------------------------------------------------------
    // Negative case -- a real ordinary name still produces a 200 (regression
    // guard for FR-1109 / SC-003).
    // ---------------------------------------------------------------------

    @Test
    void ordinaryNameStillProducesA200() throws Exception {
        String selections = String.format(VALID_TEMPLATE, jsonString("Renee"));
        mockMvc.perform(multipart("/api/v1/alter-egos")
                        .file(new MockMultipartFile("photo", "p.jpg", "image/jpeg",
                                SamplePhotos.tinyJpeg()))
                        .file(new MockMultipartFile("selections", "", "application/json",
                                selections.getBytes())))
                .andExpect(status().isOk());
    }
}

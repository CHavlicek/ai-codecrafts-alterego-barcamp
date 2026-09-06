package com.aiavatar.alterego.infrastructure.provider.gemini;

import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Materialises the JSON object returned by {@link GeminiCharacterClient}
 * into a {@link GeneratedCharacter}, enforcing 014 FR-1406 / FR-1407 /
 * FR-1408.
 *
 * <p>Validation contract (research.md §R4):
 * <ol>
 *   <li>The body is a JSON object (not array, not primitive).</li>
 *   <li>Required keys present: {@code heroTitleLine2}, {@code tagline},
 *       {@code superpowers}, {@code quote}. Extras are tolerated and ignored.</li>
 *   <li>{@code superpowers} is a JSON array of EXACTLY 3 elements (research
 *       §R3 makes the schema enforce this; the parser double-checks).</li>
 *   <li>Every string trait — {@code heroTitleLine2}, {@code tagline}, every
 *       {@code superpowers[i]}, {@code quote} — is JSON-string-typed,
 *       NFC-normalised, trimmed of leading/trailing whitespace, then
 *       required to be non-blank AND ≤ 100 Unicode code points.</li>
 *   <li>{@link GeneratedCharacter#heroTitleLine1()} is set to
 *       {@code firstName.trim().toUpperCase(Locale.ROOT)} regardless of any
 *       value the JSON may have carried — name substitution is owned here,
 *       not by the LLM (FR-1408).</li>
 * </ol>
 *
 * <p>Any violation throws {@code GenerationFailure(MALFORMED_RESPONSE)}.
 * The parser MUST NOT silently truncate, pad, or fabricate missing fields
 * (FR-1407).
 *
 * <p>Logging discipline (research.md §R11): WARN-level structured event with
 * a fixed-vocabulary {@code violation} label so operators can act on the
 * symptom without the parser ever logging the offending JSON value.
 */
@Component
public class GeminiCharacterResponseParser {

    private static final Logger log = LoggerFactory.getLogger(GeminiCharacterResponseParser.class);

    private static final String PHASE = "gemini-text-parse";
    private static final String REASON_WIRE = FallbackReason.MALFORMED_RESPONSE.wire();
    private static final int MAX_TRAIT_CODEPOINTS = 100;
    private static final int REQUIRED_SUPERPOWER_COUNT = 3;

    /**
     * Parse the LLM's JSON object into a {@link GeneratedCharacter}.
     *
     * @param body the JSON object returned by
     *             {@link GeminiCharacterClient#generateText(String, String)}.
     * @param firstName the user-supplied first name (already trimmed by the
     *                  caller; this method also trims defensively).
     * @return a validated {@link GeneratedCharacter}.
     * @throws GenerationFailure {@link FallbackReason#MALFORMED_RESPONSE}
     *                           on any FR-1406 / FR-1407 violation.
     */
    public GeneratedCharacter parse(JsonNode body, String firstName) {
        if (body == null || !body.isObject()) {
            throw malformed("non_object",
                    "Gemini text body was not a JSON object");
        }

        String heroTitleLine2 = readTrait(body, "heroTitleLine2");
        String tagline = readTrait(body, "tagline");
        List<String> superpowers = readSuperpowers(body);
        String quote = readTrait(body, "quote");

        String heroTitleLine1 = firstName == null
                ? "HERO"  // defensive — orchestrator should never pass null;
                          // matches FallbackPosterProvider.character semantics.
                : firstName.trim().toUpperCase(Locale.ROOT);

        return new GeneratedCharacter(
                heroTitleLine1,
                heroTitleLine2,
                tagline,
                superpowers,
                quote);
    }

    private String readTrait(JsonNode body, String key) {
        JsonNode node = body.get(key);
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw malformed("missing_key:" + key, "Gemini text body missing required key: " + key);
        }
        if (!node.isTextual()) {
            throw malformed("wrong_type:" + key,
                    "Gemini text body field " + key + " was not a JSON string");
        }
        return validateString(node.asText(), key);
    }

    private List<String> readSuperpowers(JsonNode body) {
        JsonNode node = body.get("superpowers");
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw malformed("missing_key:superpowers", "Gemini text body missing required key: superpowers");
        }
        if (!node.isArray()) {
            throw malformed("wrong_type:superpowers",
                    "Gemini text body field superpowers was not a JSON array");
        }
        if (node.size() != REQUIRED_SUPERPOWER_COUNT) {
            throw malformed("superpowers_count:" + node.size(),
                    "Gemini text body field superpowers had " + node.size()
                            + " entries; required exactly " + REQUIRED_SUPERPOWER_COUNT);
        }
        List<String> out = new ArrayList<>(REQUIRED_SUPERPOWER_COUNT);
        for (int i = 0; i < node.size(); i++) {
            JsonNode entry = node.get(i);
            if (entry == null || !entry.isTextual()) {
                throw malformed("wrong_type:superpowers[" + i + "]",
                        "superpowers[" + i + "] was not a JSON string");
            }
            out.add(validateString(entry.asText(), "superpowers[" + i + "]"));
        }
        return out;
    }

    /**
     * NFC-normalise, trim, then enforce non-blank and ≤ 100 codepoints.
     * The normalised+trimmed value is what survives into
     * {@link GeneratedCharacter}.
     */
    private static String validateString(String raw, String fieldLabel) {
        String normalised = Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        if (normalised.isEmpty()) {
            throw malformed("trait_blank:" + fieldLabel,
                    "Gemini text trait " + fieldLabel + " was blank after NFC + trim");
        }
        int codePoints = normalised.codePointCount(0, normalised.length());
        if (codePoints > MAX_TRAIT_CODEPOINTS) {
            throw malformed("trait_too_long:" + fieldLabel,
                    "Gemini text trait " + fieldLabel + " was " + codePoints
                            + " code points (max " + MAX_TRAIT_CODEPOINTS + ")");
        }
        return normalised;
    }

    /**
     * Emit the fixed-schema WARN line per research §R11 and return a
     * {@link GenerationFailure} carrying {@link FallbackReason#MALFORMED_RESPONSE}.
     * Never logs the offending value or the raw response body (FR-1413).
     */
    private static GenerationFailure malformed(String violation, String message) {
        log.warn("Gemini text response malformed",
                kv("phase", PHASE),
                kv("reason", REASON_WIRE),
                kv("violation", violation));
        return new GenerationFailure(FallbackReason.MALFORMED_RESPONSE, message);
    }
}

package com.aiavatar.alterego.domain.model.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Implementation of {@link ValidFirstName}.
 *
 * <p>CANONICAL RULES — keep in lock-step with
 * {@code frontend/src/features/alterego/validation/firstName.ts}.
 *
 * <ul>
 *   <li>Family A — length: code-point count of {@code NFC(trim(value))} ≤ 50</li>
 *   <li>Family B — ASCII control characters: 0x00–0x1F or 0x7F</li>
 *   <li>Family C — Unicode invisibles &amp; bidi controls:
 *       U+200B–U+200D, U+200E, U+200F, U+202A–U+202E, U+2060–U+2064,
 *       U+2066–U+2069, U+FEFF</li>
 *   <li>Family D — structural injection markers:
 *       any of {@code < > ` { } [ ] \ |}, the substring {@code ${},
 *       and a run of three or more consecutive backticks</li>
 *   <li>Family E — instruction-shaped phrases (case-insensitive,
 *       whole-word bounded by non-letter Unicode):
 *       {@code ignore previous}, {@code ignore prior},
 *       {@code ignore all previous}, {@code disregard previous},
 *       {@code system prompt}, {@code you are now}, {@code act as},
 *       {@code as an ai}, {@code assistant:}, {@code system:},
 *       {@code user:}, {@code <|}, {@code </s>}, {@code <s>},
 *       {@code prompt injection}, {@code jailbreak}</li>
 * </ul>
 *
 * <p>{@code null} is treated as valid here — {@code @NotBlank} on the
 * field handles the null/blank case independently. This keeps the
 * single-responsibility principle clean: this validator answers
 * "does the non-null value look like a real first name?"
 */
public class FirstNameValidator implements ConstraintValidator<ValidFirstName, String> {

    private static final int MAX_CODE_POINTS = 50;

    private static final Pattern UNICODE_INVISIBLE = Pattern.compile(
            "[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF]");

    private static final Pattern STRUCTURAL_MARKER = Pattern.compile(
            "[<>`{}\\[\\]\\\\|]|\\$\\{");

    private static final Pattern INSTRUCTION_PHRASE = Pattern.compile(
            "(?<![\\p{L}])("
                    + "ignore all previous|ignore previous|ignore prior|disregard previous"
                    + "|system prompt|you are now|act as|as an ai"
                    + "|assistant:|system:|user:"
                    + "|<\\||</s>|<s>"
                    + "|prompt injection|jailbreak"
                    + ")(?![\\p{L}])",
            Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        String trimmed = normalized.trim();

        // Family A -- length in code points, measured on the trimmed value.
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_CODE_POINTS) {
            return fail(context, "firstName.tooLong");
        }

        // Families B-E run on the UN-TRIMMED normalized value so leading or
        // trailing invisibles (in particular U+FEFF) cannot slip past
        // JavaScript's Unicode-aware String.prototype.trim() on the frontend
        // sibling. Java's String.trim() only strips ASCII whitespace
        // (codepoints <= 0x20), so the JS side was the strict-but-bypassable
        // one; checking pre-trim keeps the two stacks' verdicts identical.
        if (containsAsciiControl(normalized)) {
            return fail(context, "firstName.invalidChars");
        }
        if (UNICODE_INVISIBLE.matcher(normalized).find()) {
            return fail(context, "firstName.invalidChars");
        }
        if (STRUCTURAL_MARKER.matcher(normalized).find()) {
            return fail(context, "firstName.invalidChars");
        }
        if (INSTRUCTION_PHRASE.matcher(normalized).find()) {
            return fail(context, "firstName.looksLikeInstructions");
        }
        return true;
    }

    private static boolean containsAsciiControl(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c <= 0x1F || c == 0x7F) {
                return true;
            }
        }
        return false;
    }

    private static boolean fail(ConstraintValidatorContext ctx, String template) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(template).addConstraintViolation();
        return false;
    }
}

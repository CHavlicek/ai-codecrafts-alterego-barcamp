package com.aiavatar.alterego.testsupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the OpenAPI spec regardless of whether the JVM working directory
 * is the repo root or the {@code backend/} subproject. Tests that validate
 * against the contract use this so they stay portable across
 * {@code gradle test} invocations.
 *
 * <p>016 delta: resolves the v4 spec at
 * {@code specs/016-falai-image-provider/contracts/alter-egos.openapi.yaml}
 * first (additive {@code provider} field), falling back to v3 / v2 / v1
 * for any test that hasn't yet been migrated.
 */
public final class OpenApiSpecPath {

    private OpenApiSpecPath() {}

    private static final String[] CANDIDATES = {
            "../specs/016-falai-image-provider/contracts/alter-egos.openapi.yaml",
            "specs/016-falai-image-provider/contracts/alter-egos.openapi.yaml",
            "../specs/003-gemini-image-generator/contracts/alter-egos.openapi.yaml",
            "specs/003-gemini-image-generator/contracts/alter-egos.openapi.yaml",
            "../specs/002-sleek-tabbed-ui/contracts/alter-egos.openapi.yaml",
            "specs/002-sleek-tabbed-ui/contracts/alter-egos.openapi.yaml",
            "../specs/001-initial-poc/contracts/alter-egos.openapi.yaml",
            "specs/001-initial-poc/contracts/alter-egos.openapi.yaml",
    };

    /** Returns a {@code file:} URL pointing at the OpenAPI spec. */
    public static String specUrl() {
        for (String candidate : CANDIDATES) {
            Path p = Paths.get(candidate);
            if (Files.exists(p)) {
                return p.toUri().toString();
            }
        }
        throw new IllegalStateException(
                "OpenAPI spec not found in any of: " + String.join(", ", CANDIDATES));
    }
}

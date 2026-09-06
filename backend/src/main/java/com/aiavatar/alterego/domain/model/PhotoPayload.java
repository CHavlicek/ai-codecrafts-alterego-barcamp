package com.aiavatar.alterego.domain.model;

/**
 * Request-scoped holder for the user's uploaded photo. Lives only for the
 * duration of one HTTP request; never persisted, never logged, never cached
 * (FR-016 / FR-020).
 *
 * Intentionally has no {@code toString()} or {@code equals()} that would be
 * tempted to format the byte array — Records auto-generate these, but the
 * default array {@code toString} is the identity hash, so we accept the
 * default. {@code PhotoRedactionFilter} additionally drops any log line
 * referencing this object's bytes.
 *
 * @param bytes      Image bytes from the multipart {@code photo} part.
 * @param mediaType  Either "image/jpeg" or "image/png".
 */
public record PhotoPayload(byte[] bytes, String mediaType) {
    public PhotoPayload {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes MUST be non-empty");
        }
        if (mediaType == null || (!mediaType.equals("image/png") && !mediaType.equals("image/jpeg"))) {
            throw new IllegalArgumentException("mediaType MUST be image/png or image/jpeg");
        }
    }
}

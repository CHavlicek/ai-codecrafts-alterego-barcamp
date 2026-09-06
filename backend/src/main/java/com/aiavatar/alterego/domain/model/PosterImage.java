package com.aiavatar.alterego.domain.model;

import java.util.Base64;

/**
 * Output of {@link com.aiavatar.alterego.service.ImageGenerator}. The raw
 * bytes never cross the wire as-is — {@link #toDataUrl()} encodes them as
 * a {@code data:} URL that the frontend renders directly with a single
 * {@code <img src=…>} (research.md R3).
 *
 * @param bytes      Raw image bytes (PNG or JPEG depending on {@code mediaType}).
 * @param mediaType  Either "image/png" or "image/jpeg".
 * @param widthPx    Pixel width.
 * @param heightPx   Pixel height.
 */
public record PosterImage(
        byte[] bytes,
        String mediaType,
        int widthPx,
        int heightPx
) {
    public PosterImage {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("bytes MUST be non-empty");
        }
        if (mediaType == null || (!mediaType.equals("image/png") && !mediaType.equals("image/jpeg"))) {
            throw new IllegalArgumentException("mediaType MUST be image/png or image/jpeg");
        }
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("widthPx and heightPx MUST be positive");
        }
    }

    public String toDataUrl() {
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}

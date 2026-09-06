package com.aiavatar.alterego.infrastructure.photo;

import com.aiavatar.alterego.domain.model.PhotoPayload;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Reduces the user's photo to fit inside a real provider's per-request limits
 * before the outbound call. Originally introduced by 003 for Gemini
 * (003 FR-206..209); refactored in 016 to a provider-neutral component that
 * takes its thresholds via a {@link PhotoReductionConfig} parameter so both
 * the Gemini path (003) and the fal.ai path (016) can reuse it
 * (016 research.md R4 / FR-1616).
 *
 * <p>Pass-through semantics: if the photo is already within both the byte
 * ceiling ({@link PhotoReductionConfig#maxBytes()}) and the pixel ceiling
 * ({@link PhotoReductionConfig#maxLongestEdge()}), the original bytes are
 * returned unchanged — no re-encoding, no quality loss
 * (003 FR-207 / 016 FR-1616).
 *
 * <p>Over-threshold reduction: scale the longest edge to
 * {@link PhotoReductionConfig#reducedTargetLongestEdge()} and re-encode as
 * JPEG at {@link PhotoReductionConfig#reducedJpegQuality()}. Thumbnailator
 * handles EXIF orientation automatically so portrait phone photos stay
 * upright.
 *
 * <p>Never up-scales (003 FR-207). Stateless &amp; thread-safe.
 */
@Component
public class PhotoReducer {

    /**
     * @param photo  the user-supplied photo (already validated mime + non-empty
     *               bytes by {@link PhotoPayload}'s constructor)
     * @param config provider-tuned reduction thresholds (003 Gemini or 016 fal.ai)
     * @return either {@code photo} itself (pass-through) or a new reduced
     *         {@link PhotoPayload} with {@code mediaType == "image/jpeg"}
     */
    public PhotoPayload reduce(PhotoPayload photo, PhotoReductionConfig config) {
        int longestEdge = readLongestEdge(photo.bytes());
        boolean withinByteCeiling = photo.bytes().length <= config.maxBytes();
        boolean withinPixelCeiling = longestEdge <= config.maxLongestEdge();
        if (withinByteCeiling && withinPixelCeiling) {
            // Pass-through — no re-encode, preserves source quality (FR-207).
            return photo;
        }
        return reencodeAsJpeg(photo, config);
    }

    private int readLongestEdge(byte[] bytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                // Unreadable — let the downstream provider call surface the
                // issue as a provider error; don't block the reduction path.
                return Integer.MAX_VALUE;
            }
            return Math.max(img.getWidth(), img.getHeight());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read photo dimensions", e);
        }
    }

    private PhotoPayload reencodeAsJpeg(PhotoPayload photo, PhotoReductionConfig config) {
        int target = config.reducedTargetLongestEdge();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Thumbnails.of(new ByteArrayInputStream(photo.bytes()))
                    .size(target, target)
                    .outputFormat("jpg")
                    .outputQuality(config.reducedJpegQuality())
                    .toOutputStream(out);
            return new PhotoPayload(out.toByteArray(), "image/jpeg");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to reduce photo", e);
        }
    }
}

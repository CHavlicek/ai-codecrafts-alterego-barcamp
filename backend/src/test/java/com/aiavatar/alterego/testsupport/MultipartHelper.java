package com.aiavatar.alterego.testsupport;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Builds the multipart {@code HttpEntity} bodies that the integration tests
 * POST to {@code /api/v1/alter-egos}. Centralised so the photo-part /
 * selections-part wiring isn't repeated across every test class.
 *
 * <p>The photo part is wrapped in a {@link ByteArrayResource} subclass with
 * an overridden {@code getFilename()}: this is what makes
 * {@code FormHttpMessageConverter} write a {@code Content-Disposition} with
 * a {@code filename=} attribute, which is in turn what Spring's
 * {@code StandardMultipartHttpServletRequest} requires before it routes the
 * part into its {@code multipartFiles} map (where {@code @RequestPart
 * MultipartFile} can find it). Plain {@code byte[]} parts get filed as
 * form parameters and the controller responds with {@code 400 "Required
 * part 'photo' is not present"}.
 */
public final class MultipartHelper {

    private MultipartHelper() {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static HttpEntity<MultiValueMap<String, Object>> generateRequest(
            byte[] photoBytes,
            String photoMime,
            AlterEgoRequest selections) {
        return generateRequest(photoBytes, photoMime, selections, null);
    }

    public static HttpEntity<MultiValueMap<String, Object>> generateRequest(
            byte[] photoBytes,
            String photoMime,
            AlterEgoRequest selections,
            String correlationId) {
        HttpHeaders outerHeaders = new HttpHeaders();
        outerHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (correlationId != null) {
            outerHeaders.set("X-Request-Id", correlationId);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        String filename = "photo." + ("image/png".equals(photoMime) ? "png" : "jpg");
        ByteArrayResource photoResource = new NamedByteArrayResource(photoBytes, filename);
        HttpHeaders photoHeaders = new HttpHeaders();
        photoHeaders.setContentType(MediaType.parseMediaType(photoMime));
        body.add("photo", new HttpEntity<>(photoResource, photoHeaders));

        HttpHeaders selectionsHeaders = new HttpHeaders();
        selectionsHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("selections", new HttpEntity<>(serialize(selections), selectionsHeaders));

        return new HttpEntity<>(body, outerHeaders);
    }

    private static String serialize(AlterEgoRequest selections) {
        try {
            return MAPPER.writeValueAsString(selections);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize selections", e);
        }
    }

    /** ByteArrayResource that reports a filename — required for the part to be parsed as a file upload. */
    private static final class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}

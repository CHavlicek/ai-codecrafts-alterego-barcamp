package com.aiavatar.alterego.domain.model;

import com.aiavatar.alterego.domain.model.validation.ValidFirstName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 023 (issue #57) — Bean-Validation carrier for the {@code to} + {@code firstName}
 * text parts of the {@code POST /api/v1/alter-egos/email} multipart request.
 *
 * <p>The image part travels as a separate {@link org.springframework.web.multipart.MultipartFile}
 * controller parameter — mirroring how {@link AlterEgoRequest} sits next
 * to the {@code photo} {@code MultipartFile} on the Generate endpoint.
 *
 * <p>Validation rules:
 * <ul>
 *   <li>{@code to}: {@link NotBlank} + {@link Email} + {@link Size}(max=254).
 *       254 is the practical RFC 5321 envelope cap (research R7).</li>
 *   <li>{@code firstName}: {@link NotBlank} + {@link Size}(max=50) +
 *       {@link ValidFirstName} — reuses 011's canonical validator so the
 *       same families (Unicode invisibles, structural markers,
 *       instruction-shaped phrases) gate this endpoint too.</li>
 * </ul>
 */
public record SendAlterEgoEmailRequest(
        @NotBlank @Email @Size(max = 254) String to,
        @NotBlank @Size(max = 50) @ValidFirstName String firstName) {}

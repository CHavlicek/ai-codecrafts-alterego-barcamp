package com.aiavatar.alterego.domain.model;

/**
 * 023 (issue #57) — terminal 200 OK response for the
 * {@code POST /api/v1/alter-egos/email} endpoint. The status field is a
 * stable contract; the FE classifies on HTTP code AND this value so
 * forward-compatible servers can extend the taxonomy without breaking
 * the existing classifier.
 */
public record SendAlterEgoEmailResponse(String status) {

    public static SendAlterEgoEmailResponse sent() {
        return new SendAlterEgoEmailResponse("sent");
    }
}

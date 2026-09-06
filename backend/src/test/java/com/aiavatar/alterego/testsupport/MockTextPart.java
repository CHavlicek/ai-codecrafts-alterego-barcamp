package com.aiavatar.alterego.testsupport;

import jakarta.servlet.http.Part;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

/**
 * 023 — lightweight {@link Part} for non-file multipart sections used
 * by the {@code POST /api/v1/alter-egos/email} integration tests.
 * {@code MockMvc}'s {@code multipart(...).part(Part)} accepts any
 * implementation; we provide one that holds a UTF-8 string value
 * without falsifying it as a file.
 */
public record MockTextPart(String name, String value) implements Part {

    public static Part of(String name, String value) {
        return new MockTextPart(name, value);
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getContentType() {
        return "text/plain;charset=UTF-8";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getSubmittedFileName() {
        return null;
    }

    @Override
    public long getSize() {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    @Override
    public void write(String fileName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete() {
        // no-op
    }

    @Override
    public String getHeader(String n) {
        return null;
    }

    @Override
    public Collection<String> getHeaders(String n) {
        return List.of();
    }

    @Override
    public Collection<String> getHeaderNames() {
        return List.of();
    }
}

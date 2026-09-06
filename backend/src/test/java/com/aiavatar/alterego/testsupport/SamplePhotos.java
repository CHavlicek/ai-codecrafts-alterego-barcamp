package com.aiavatar.alterego.testsupport;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Small JPEG/PNG byte arrays for use as multipart {@code photo} parts in
 * controller / integration tests. Generated in-process so we don't bundle
 * any binary fixtures.
 */
public final class SamplePhotos {

    private SamplePhotos() {}

    public static byte[] tinyJpeg() {
        return encode("JPEG", 64, 64, new Color(120, 80, 60));
    }

    public static byte[] tinyPng() {
        return encode("PNG", 64, 64, new Color(40, 200, 120));
    }

    private static byte[] encode(String format, int width, int height, Color fill) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(fill);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(img, format, out)) {
                throw new IllegalStateException("No writer registered for " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode " + format + " sample", e);
        }
    }
}

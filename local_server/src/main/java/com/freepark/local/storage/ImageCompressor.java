package com.freepark.local.storage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

/**
 * 云上传前按字节上限压缩图片。解码失败则原样返回，避免丢掉抓拍。
 */
public final class ImageCompressor {

    public static final int DEFAULT_MAX_KB = 200;
    public static final int MIN_KB = 20;
    public static final int MAX_KB = 5120;

    private static final float[] QUALITIES = {0.85f, 0.7f, 0.55f, 0.4f, 0.28f};
    private static final double[] SCALES = {1.0, 0.85, 0.7, 0.55, 0.4};

    public record Result(byte[] bytes, String mime) {
    }

    private ImageCompressor() {
    }

    public static Result limit(byte[] bytes, String mime, int maxBytes) {
        if (bytes == null || bytes.length == 0) {
            return new Result(bytes, mime);
        }
        if (maxBytes <= 0 || bytes.length <= maxBytes) {
            return new Result(bytes, mime == null || mime.isBlank() ? "image/jpeg" : mime);
        }
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return new Result(bytes, mime);
        }
        if (decoded == null) {
            return new Result(bytes, mime);
        }
        BufferedImage rgb = toRgb(decoded);
        byte[] best = bytes;
        try {
            for (double scale : SCALES) {
                BufferedImage frame = scale == 1.0 ? rgb : scale(rgb, scale);
                for (float quality : QUALITIES) {
                    byte[] jpeg = encodeJpeg(frame, quality);
                    if (jpeg.length > 0 && jpeg.length < best.length) {
                        best = jpeg;
                    }
                    if (jpeg.length > 0 && jpeg.length <= maxBytes) {
                        return new Result(jpeg, "image/jpeg");
                    }
                }
            }
        } catch (IOException e) {
            return new Result(bytes, mime);
        }
        return new Result(best, best == bytes ? mime : "image/jpeg");
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        graphics.drawImage(src, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    private static BufferedImage scale(BufferedImage src, double scale) {
        int width = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = dst.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(src, 0, 0, width, height, null);
        graphics.dispose();
        return dst;
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("no jpeg writer");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            JPEGImageWriteParam param = new JPEGImageWriteParam(null);
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
            ios.flush();
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}

package com.freepark.local.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

import org.junit.jupiter.api.Test;

class ImageCompressorTest {

    @Test
    void keepsOriginalWhenAlreadyUnderLimit() {
        byte[] original = new byte[] {1, 2, 3, 4};
        ImageCompressor.Result result = ImageCompressor.limit(original, "image/jpeg", 10_000);
        assertSame(original, result.bytes());
        assertEquals("image/jpeg", result.mime());
    }

    @Test
    void compressesNoisyJpegUnderLimit() throws Exception {
        byte[] original = noisyJpeg(900, 700, 0.95f);
        assertTrue(original.length > 40_000, "fixture should exceed 40KB, was " + original.length);
        ImageCompressor.Result result = ImageCompressor.limit(original, "image/jpeg", 30_000);
        assertTrue(result.bytes().length <= 30_000, "compressed size " + result.bytes().length);
        assertTrue(result.bytes().length < original.length);
        assertEquals("image/jpeg", result.mime());
    }

    private static byte[] noisyJpeg(int width, int height, float quality) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = ((x * 47 + y * 91) & 0xff) << 16
                        | ((x * 13 + y * 7) & 0xff) << 8
                        | ((x * 3 + y * 29) & 0xff);
                image.setRGB(x, y, rgb);
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
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

package com.freepark.local.storage;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class CloudObjectSigner {

    private static final DateTimeFormatter RFC1123 = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private CloudObjectSigner() {
    }

    static String rfc1123(Instant instant) {
        return RFC1123.format(instant);
    }

    static String amzDate(Instant instant) {
        return AMZ_DATE.format(instant);
    }

    static String aliyunAuthorization(
            String method,
            String bucket,
            String objectKey,
            String contentType,
            String date,
            String accessKeyId,
            String accessKeySecret) {
        String stringToSign = method + "\n\n" + contentType + "\n" + date + "\n/" + bucket + "/" + objectKey;
        String signature = Base64.getEncoder().encodeToString(hmac("HmacSHA1", accessKeySecret.getBytes(StandardCharsets.UTF_8),
                stringToSign.getBytes(StandardCharsets.UTF_8)));
        return "OSS " + accessKeyId + ":" + signature;
    }

    static Map<String, String> awsV4Headers(
            String method,
            String host,
            String objectKey,
            String contentType,
            byte[] body,
            Instant now,
            String region,
            String accessKey,
            String secretKey) {
        String amzDate = amzDate(now);
        String datestamp = amzDate.substring(0, 8);
        String payloadHash = sha256Hex(body);
        String canonicalUri = "/" + CloudObjectKeys.encodePath(objectKey);
        String canonicalHeaders = "content-type:" + contentType + "\n"
                + "host:" + host + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date";
        String canonicalRequest = method + "\n"
                + canonicalUri + "\n"
                + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;
        String credentialScope = datestamp + "/" + region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        byte[] signingKey = hmacSha256(
                hmacSha256(
                        hmacSha256(
                                hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), datestamp),
                                region),
                        "s3"),
                "aws4_request");
        String signature = HexFormat.of().formatHex(hmacSha256(signingKey, stringToSign));
        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("x-amz-content-sha256", payloadHash);
        headers.put("x-amz-date", amzDate);
        headers.put("Authorization", authorization);
        return headers;
    }

    private static byte[] hmacSha256(byte[] key, String message) {
        return hmac("HmacSHA256", key, message.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(String algorithm, byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

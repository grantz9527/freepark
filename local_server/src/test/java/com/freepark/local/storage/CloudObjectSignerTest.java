package com.freepark.local.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CloudObjectSignerTest {

    @Test
    void aliyunAuthorizationMatchesKnownVector() {
        String date = CloudObjectSigner.rfc1123(Instant.EPOCH);
        assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", date);
        assertEquals(
                "OSS AKID:m7s/QekiQCpqw/XzLbpgJDrxiMA=",
                CloudObjectSigner.aliyunAuthorization(
                        "PUT",
                        "bucket",
                        "dir/file.jpg",
                        "application/octet-stream",
                        date,
                        "AKID",
                        "secret"));
    }

    @Test
    void awsV4AuthorizationIsDeterministicAndScoped() {
        Instant now = Instant.parse("2015-08-30T12:36:00Z");
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        Map<String, String> first = CloudObjectSigner.awsV4Headers(
                "PUT",
                "mybucket.obs.cn-north-4.myhuaweicloud.com",
                "freepark-local/recognition/file.jpg",
                "image/jpeg",
                body,
                now,
                "cn-north-4",
                "AKID",
                "secret");
        Map<String, String> second = CloudObjectSigner.awsV4Headers(
                "PUT",
                "mybucket.obs.cn-north-4.myhuaweicloud.com",
                "freepark-local/recognition/file.jpg",
                "image/jpeg",
                body,
                now,
                "cn-north-4",
                "AKID",
                "secret");
        String authorization = first.get("Authorization");
        assertEquals(first, second);
        assertEquals("20150830T123600Z", first.get("x-amz-date"));
        assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 Credential=AKID/20150830/cn-north-4/s3/aws4_request"));
        assertTrue(authorization.contains("SignedHeaders=content-type;host;x-amz-content-sha256;x-amz-date"));
        assertTrue(authorization.matches(".*, Signature=[0-9a-f]{64}"));
    }
}

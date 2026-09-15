package com.freepark.local.streampreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.freepark.local.common.exception.BusinessException;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.streampreview.dto.StreamPreviewSourceView;

class StreamPreviewServiceTest {

    @Test
    void go2rtcStreamMp4EncodesRtspSource() {
        URI uri = StreamPreviewUrls.go2rtcStreamMp4(
                "http://127.0.0.1:1984", "rtsp://user:pass@192.168.1.64:554/h264");
        assertEquals("127.0.0.1", uri.getHost());
        assertEquals(1984, uri.getPort());
        assertEquals("/api/stream.mp4", uri.getPath());
        assertTrue(uri.getRawQuery().startsWith("src="));
        assertTrue(uri.getRawQuery().contains("rtsp"));
        assertTrue(uri.getRawQuery().contains("%40") || uri.getQuery().contains("@"));
    }

    @Test
    void prefersFrigateApiProxyThenGo2rtcPort() {
        assertEquals(
                "http://127.0.0.1:5000/api/go2rtc",
                StreamPreviewUrls.frigateGo2rtcProxyBase("127.0.0.1", 5000));
        assertEquals("http://127.0.0.1:1984", StreamPreviewUrls.go2rtcBase("127.0.0.1", 1984));
    }

    @Test
    void requireTranscodableAcceptsFrigateCameraName() {
        assertEquals("cam_acfb0350", StreamPreviewUrls.requireTranscodable(" cam_acfb0350 "));
    }

    @Test
    void requireTranscodableAcceptsRtsp() {
        assertEquals(
                "rtsp://192.168.1.64:554/h264/ch1/main/av_stream",
                StreamPreviewUrls.requireTranscodable(" rtsp://192.168.1.64:554/h264/ch1/main/av_stream "));
    }

    @Test
    void requireTranscodableRejectsFriendlyChineseName() {
        BusinessException ex = assertThrows(
                BusinessException.class, () -> StreamPreviewUrls.requireTranscodable("测试"));
        assertEquals(ErrorCode.STREAM_URL_INVALID, ex.errorCode());
    }

    @Test
    void resolveSourceNameMapsFriendlyLabelToGo2rtcId() {
        var sources = List.of(new StreamPreviewSourceView("cam_acfb0350", "测试"));
        assertEquals("cam_acfb0350", StreamPreviewService.resolveSourceName("测试", sources));
        assertEquals("cam_acfb0350", StreamPreviewService.resolveSourceName(" cam_acfb0350 ", sources));
        assertEquals("未知相机", StreamPreviewService.resolveSourceName("未知相机", sources));
    }

    @Test
    void requireTranscodableRejectsFileScheme() {
        BusinessException ex = assertThrows(
                BusinessException.class, () -> StreamPreviewUrls.requireTranscodable("file:///tmp/video.mp4"));
        assertEquals(ErrorCode.STREAM_URL_INVALID, ex.errorCode());
    }

    @Test
    void requireTranscodableRejectsMissingHost() {
        BusinessException ex = assertThrows(
                BusinessException.class, () -> StreamPreviewUrls.requireTranscodable("rtsp:///no-host"));
        assertEquals(ErrorCode.STREAM_URL_INVALID, ex.errorCode());
    }
}

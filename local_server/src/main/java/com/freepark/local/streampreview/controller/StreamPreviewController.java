package com.freepark.local.streampreview.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.streampreview.StreamPreviewService;
import com.freepark.local.streampreview.StreamPreviewService.PreviewPipe;
import com.freepark.local.streampreview.dto.StreamPreviewRequest;
import com.freepark.local.streampreview.dto.StreamPreviewSourceView;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/barriers/stream-preview")
public class StreamPreviewController {

    private static final MediaType VIDEO_MP4 = MediaType.parseMediaType("video/mp4");

    private final StreamPreviewService streamPreviewService;
    private final MessageService messages;

    public StreamPreviewController(StreamPreviewService streamPreviewService, MessageService messages) {
        this.streamPreviewService = streamPreviewService;
        this.messages = messages;
    }

    @GetMapping("/sources")
    public ApiResponse<List<StreamPreviewSourceView>> sources() {
        return ApiResponse.ok(messages, streamPreviewService.listSources());
    }

    @PostMapping(produces = "video/mp4")
    public ResponseEntity<StreamingResponseBody> preview(@Valid @RequestBody StreamPreviewRequest request) {
        PreviewPipe pipe = streamPreviewService.open(request.streamUrl());
        StreamingResponseBody body = output -> {
            try (pipe) {
                pipe.copyTo(output);
            } catch (IOException ignored) {
                // 预览窗口关闭或浏览器中断拉流
            }
        };
        return ResponseEntity.ok()
                .contentType(VIDEO_MP4)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache")
                .header("Pragma", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }
}

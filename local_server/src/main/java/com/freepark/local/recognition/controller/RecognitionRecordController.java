package com.freepark.local.recognition.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.domain.RecognitionEventType;
import com.freepark.local.recognition.dto.CreateRecognitionRecordRequest;
import com.freepark.local.recognition.dto.MarkAbnormalRequest;
import com.freepark.local.recognition.dto.ParkingFlowResult;
import com.freepark.local.recognition.dto.RecognitionRecordView;
import com.freepark.local.recognition.service.RecognitionRecordService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/recognition-records")
public class RecognitionRecordController {

    private final RecognitionRecordService recognitionRecordService;
    private final MessageService messages;

    public RecognitionRecordController(
            RecognitionRecordService recognitionRecordService,
            MessageService messages) {
        this.recognitionRecordService = recognitionRecordService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<RecognitionRecordView>> list(
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) UUID laneId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) RecognitionEventType eventType,
            @RequestParam(required = false) Boolean abnormalOnly) {
        return ApiResponse.ok(
                messages,
                recognitionRecordService.listRecords(lotId, laneId, keyword, eventType, abnormalOnly));
    }

    @PostMapping
    public ApiResponse<ParkingFlowResult> create(@Valid @RequestBody CreateRecognitionRecordRequest request) {
        return ApiResponse.ok(messages, recognitionRecordService.createManualRecord(request));
    }

    @PostMapping("/{recordId}/abnormal")
    public ApiResponse<RecognitionRecordView> markAbnormal(
            @PathVariable UUID recordId,
            @Valid @RequestBody(required = false) MarkAbnormalRequest request) {
        return ApiResponse.ok(
                messages,
                recognitionRecordService.markAbnormal(
                        recordId, request == null ? null : request.reason()));
    }

    @PostMapping("/{recordId}/voided")
    public ApiResponse<RecognitionRecordView> markVoided(@PathVariable UUID recordId) {
        return ApiResponse.ok(messages, recognitionRecordService.markVoided(recordId));
    }
}

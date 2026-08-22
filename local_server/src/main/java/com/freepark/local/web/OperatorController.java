package com.freepark.local.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.operator.CreateOperatorRequest;
import com.freepark.local.operator.OperatorService;
import com.freepark.local.operator.OperatorView;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/operators")
public class OperatorController {

    private final OperatorService operatorService;
    private final MessageService messages;

    public OperatorController(OperatorService operatorService, MessageService messages) {
        this.operatorService = operatorService;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<List<OperatorView>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(messages, operatorService.listOperators(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping
    public ApiResponse<OperatorView> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOperatorRequest request) {
        return ApiResponse.ok(messages, operatorService.createOperator(UUID.fromString(jwt.getSubject()), request));
    }
}

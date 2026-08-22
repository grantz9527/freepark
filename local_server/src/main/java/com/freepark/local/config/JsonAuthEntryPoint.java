package com.freepark.local.config;

import java.io.IOException;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.freepark.local.common.api.ApiResponse;
import com.freepark.local.common.exception.ErrorCode;
import com.freepark.local.common.i18n.MessageService;
import com.freepark.local.common.i18n.SupportedLocale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JsonAuthEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final MessageService messages;
    private final JsonMapper jsonMapper;

    public JsonAuthEntryPoint(MessageService messages, JsonMapper jsonMapper) {
        this.messages = messages;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        write(request, response, ErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        write(request, response, ErrorCode.UNAUTHORIZED);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode errorCode) throws IOException {
        Locale locale = localeOf(request);
        response.setStatus(errorCode.status().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(),
                ApiResponse.fail(errorCode.code(), messages.get(locale, errorCode.messageKey())));
    }

    private Locale localeOf(HttpServletRequest request) {
        String lang = request.getParameter("lang");
        if (lang != null && !lang.isBlank()) {
            return SupportedLocale.resolve(lang);
        }
        String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        if (header != null && !header.isBlank()) {
            return SupportedLocale.resolve(header.split(",")[0].trim());
        }
        return SupportedLocale.defaultLocale();
    }
}

package com.freepark.cloud.web;

import java.util.List;
import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.freepark.cloud.common.api.ApiResponse;
import com.freepark.cloud.common.i18n.MessageService;
import com.freepark.cloud.common.i18n.SupportedLocale;

@RestController
@RequestMapping("/api/v1/i18n")
public class I18nController {

    private final MessageService messages;

    public I18nController(MessageService messages) {
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<I18nView> current() {
        Locale locale = LocaleContextHolder.getLocale();
        return ApiResponse.ok(messages, new I18nView(
                locale.toLanguageTag(),
                messages.get("app.welcome"),
                SupportedLocale.all().stream().map(Locale::toLanguageTag).toList()));
    }

    public record I18nView(String locale, String welcome, List<String> supportedLocales) {
    }
}

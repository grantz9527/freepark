package com.freepark.local.common.i18n;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class MessageService {

    private final MessageSource messageSource;

    public MessageService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String code, Object... args) {
        return get(LocaleContextHolder.getLocale(), code, args);
    }

    public String get(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, code, locale);
    }
}

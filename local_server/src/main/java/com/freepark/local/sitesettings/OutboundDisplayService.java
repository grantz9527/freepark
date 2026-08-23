package com.freepark.local.sitesettings;

import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.freepark.local.common.i18n.MessageService;

/**
 * Resolves locale and timezone for outbound channels (email, SMS, hardware displays)
 * using site-wide system settings rather than the current HTTP request locale.
 */
@Service
public class OutboundDisplayService {

    private final SystemSettingsService systemSettings;
    private final MessageService messages;

    public OutboundDisplayService(SystemSettingsService systemSettings, MessageService messages) {
        this.systemSettings = systemSettings;
        this.messages = messages;
    }

    public Locale locale() {
        return systemSettings.getDefaultLocale();
    }

    public String formatTime(Instant instant) {
        return systemSettings.formatInstant(instant);
    }

    public String message(String code, Object... args) {
        return messages.get(locale(), code, args);
    }
}

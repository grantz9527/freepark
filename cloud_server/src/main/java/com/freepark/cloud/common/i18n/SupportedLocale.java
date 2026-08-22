package com.freepark.cloud.common.i18n;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum SupportedLocale {

    EN(Locale.ENGLISH),
    ZH_CN(Locale.SIMPLIFIED_CHINESE),
    ZH_TW(Locale.TRADITIONAL_CHINESE),
    JA(Locale.JAPANESE),
    KO(Locale.KOREAN),
    ES(Locale.of("es")),
    FR(Locale.FRENCH),
    DE(Locale.GERMAN),
    PT(Locale.of("pt")),
    AR(Locale.of("ar"));

    private final Locale locale;

    SupportedLocale(Locale locale) {
        this.locale = locale;
    }

    public Locale locale() {
        return locale;
    }

    public static Locale defaultLocale() {
        return EN.locale;
    }

    public static List<Locale> all() {
        return Arrays.stream(values()).map(SupportedLocale::locale).toList();
    }

    public static Locale resolve(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return defaultLocale();
        }
        return resolve(Locale.forLanguageTag(languageTag.trim().replace('_', '-')));
    }

    public static Locale resolve(Locale requested) {
        if (requested == null) {
            return defaultLocale();
        }

        String language = requested.getLanguage();
        String country = requested.getCountry();
        String script = requested.getScript();

        if ("zh".equals(language)) {
            if ("Hant".equalsIgnoreCase(script)
                    || "TW".equalsIgnoreCase(country)
                    || "HK".equalsIgnoreCase(country)
                    || "MO".equalsIgnoreCase(country)) {
                return ZH_TW.locale;
            }
            return ZH_CN.locale;
        }

        for (SupportedLocale item : values()) {
            if (item.locale.getLanguage().equalsIgnoreCase(language)
                    && (item.locale.getCountry().isEmpty()
                            || country.isEmpty()
                            || item.locale.getCountry().equalsIgnoreCase(country))) {
                return item.locale;
            }
        }

        return defaultLocale();
    }
}

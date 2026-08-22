package com.freepark.cloud.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;

import com.freepark.cloud.common.i18n.SupportedLocale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class I18nConfig {

    public static final String LANG_PARAM = "lang";

    @Bean
    MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:i18n/messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(SupportedLocale.defaultLocale());
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }

    @Bean
    LocaleResolver localeResolver() {
        return new RequestLocaleResolver();
    }

    /**
     * Resolves locale from {@code lang} query parameter first, then {@code Accept-Language}.
     */
    static final class RequestLocaleResolver implements LocaleResolver {

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            String lang = request.getParameter(LANG_PARAM);
            if (lang != null && !lang.isBlank()) {
                return SupportedLocale.resolve(lang);
            }

            String header = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
            if (header != null && !header.isBlank()) {
                try {
                    List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
                    Locale matched = Locale.lookup(ranges, SupportedLocale.all());
                    if (matched != null) {
                        return SupportedLocale.resolve(matched.toLanguageTag());
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid Accept-Language headers fall back to the default locale.
                }
            }

            return SupportedLocale.defaultLocale();
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
            // REST APIs are stateless; locale is resolved per request.
        }
    }
}

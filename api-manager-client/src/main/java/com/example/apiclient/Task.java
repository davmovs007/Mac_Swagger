package com.example.apiclient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record Task(Long id, String title, String description, TaskStatus status, String createdAt) {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.forLanguageTag("ru"))
                    .withZone(ZoneId.systemDefault());

    public static String formatCreatedAt(String value) {
        if (value == null || value.isBlank()) {
            return "дата неизвестна";
        }
        try {
            return DATE_FORMAT.format(Instant.parse(value));
        } catch (RuntimeException exception) {
            return value;
        }
    }
}

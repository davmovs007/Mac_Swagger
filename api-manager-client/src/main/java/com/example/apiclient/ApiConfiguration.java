package com.example.apiclient;

import java.net.URI;

public final class ApiConfiguration {
    private ApiConfiguration() {
    }

    public static URI baseUri() {
        String configured = System.getProperty("tasktracker.api.base-url",
                System.getenv().getOrDefault("TASKTRACKER_API_BASE_URL", "http://localhost:8080"));
        try {
            URI uri = URI.create(configured.trim());
            if (uri.getScheme() == null || uri.getHost() == null
                    || !(uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Некорректный адрес API: " + configured, exception);
        }
    }
}

package com.example.apiclient;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class TaskApiClient {
    private static final Type TASK_PAGE_TYPE = new TypeToken<PagedTasks>() { }.getType();
    private static final String TASKS_PATH = "/api/v1/tasks";

    private final URI baseUrl;
    private final Gson gson = new Gson();

    TaskApiClient(URI baseUrl) {
        String value = baseUrl.toString();
        this.baseUrl = URI.create(value.endsWith("/") ? value.substring(0, value.length() - 1) : value);
    }

    URI baseUrl() {
        return baseUrl;
    }

    CompletableFuture<PagedTasks> listTasks(int page, int size, TaskStatus status, String query) {
        return execute(() -> {
            RequestSpecification request = request().queryParam("page", page).queryParam("size", size);
            if (status != null) {
                request.queryParam("status", status.name());
            }
            if (query != null && !query.isBlank()) {
                request.queryParam("query", query.trim());
            }
            Response response = request.when().get(TASKS_PATH);
            ensureSuccess(response);
            return gson.fromJson(response.asString(), TASK_PAGE_TYPE);
        });
    }

    CompletableFuture<Task> getTask(long id) {
        return execute(() -> {
            Response response = request().when().get(TASKS_PATH + "/" + id);
            ensureSuccess(response);
            return gson.fromJson(response.asString(), Task.class);
        });
    }

    CompletableFuture<Task> createTask(TaskInput input) {
        return execute(() -> {
            Response response = request().body(gson.toJson(input)).when().post(TASKS_PATH);
            ensureSuccess(response);
            return gson.fromJson(response.asString(), Task.class);
        });
    }

    CompletableFuture<Task> updateTask(long id, TaskInput input) {
        return execute(() -> {
            Response response = request().body(gson.toJson(input)).when().put(TASKS_PATH + "/" + id);
            ensureSuccess(response);
            return gson.fromJson(response.asString(), Task.class);
        });
    }

    CompletableFuture<Void> deleteTask(long id) {
        return execute(() -> {
            Response response = request().when().delete(TASKS_PATH + "/" + id);
            ensureSuccess(response);
            return null;
        });
    }

    private RequestSpecification request() {
        return RestAssured.given()
                .baseUri(baseUrl.toString())
                .config(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 5_000)
                        .setParam("http.socket.timeout", 15_000)))
                .accept(ContentType.JSON)
                .contentType(ContentType.JSON);
    }

    private <T> CompletableFuture<T> execute(Supplier<T> request) {
        return CompletableFuture.supplyAsync(request);
    }

    private void ensureSuccess(Response response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(errorMessage(response));
        }
    }

    private String errorMessage(Response response) {
        String message = null;
        try {
            JsonElement parsed = JsonParser.parseString(response.asString());
            if (parsed.isJsonObject()) {
                JsonObject body = parsed.getAsJsonObject();
                message = firstValue(body, "detail", "message", "error");
            }
        } catch (RuntimeException ignored) {
            // The HTTP status remains useful when the server returns a non-JSON error.
        }
        return message == null
                ? "Сервер ответил с кодом " + response.statusCode()
                : message + " (код " + response.statusCode() + ")";
    }

    private String firstValue(JsonObject body, String... names) {
        for (String name : names) {
            JsonElement value = body.get(name);
            if (value != null && value.isJsonPrimitive() && !value.getAsString().isBlank()) {
                return value.getAsString();
            }
        }
        return null;
    }
}

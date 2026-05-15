package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.http.enums.HttpStatus;

/**
 * Базовый обработчик HTTP‑запросов, содержащий общие утилиты для работы
 * с JSON‑ответами.
 */
public abstract class BaseHttpHandler implements HttpHandler {

    protected static final String CT_JSON = "application/json; charset=UTF-8";
    private static final Gson gson = new GsonBuilder().create();

    /**
     * Отправляет объект в формате JSON.
     */
    protected void sendJson(HttpExchange ex, int status, Object obj) throws IOException {
        String json = gson.toJson(obj);
        byte[] bodyBytes = json.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, bodyBytes.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bodyBytes);
        }
    }

    /**
     * Отправляет ответ 204 No Content.
     */
    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(HttpStatus.NO_CONTENT.getCode(), -1);
    }

    /**
     * Формирует JSON‑ответ об ошибке.
     */
    protected void sendError(HttpExchange ex, int status, ErrorResponse error) throws IOException {
        sendJson(ex, status, error);
    }
}
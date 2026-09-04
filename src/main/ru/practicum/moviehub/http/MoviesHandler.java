package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.net.URI;
import java.net.URLDecoder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.http.enums.HttpStatus;
import ru.practicum.moviehub.http.service.MovieService;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

/**
 * Обработчик HTTP‑запросов к эндпоинту /movies.
 */
public class MoviesHandler extends BaseHttpHandler {

    private final MovieService service;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public MoviesHandler(MoviesStore store) {
        this.service = new MovieService(store);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            URI uri = ex.getRequestURI();
            String path = uri.getPath();
            String subPath = path.substring("/movies".length());

            switch (method.toUpperCase(Locale.ROOT)) {
                case "GET" -> handleGet(ex, subPath, uri.getQuery());
                case "POST" -> handlePost(ex);
                case "DELETE" -> handleDelete(ex, subPath);
                default -> ex.sendResponseHeaders(HttpStatus.METHOD_NOT_ALLOWED.getCode(), -1);
            }
        } catch (Exception e) {
            ex.sendResponseHeaders(HttpStatus.INTERNAL_SERVER_ERROR.getCode(), -1);
        }
    }

    private void handleGet(HttpExchange ex, String subPath, String query) throws IOException {
        if (subPath.isEmpty() || subPath.equals("/")) {
            Integer yearFilter = null;
            if (query != null && !query.isEmpty()) {
                Map<String, String> params = parseQuery(query);
                String yearStr = params.get("year");
                if (yearStr == null) {
                    sendError(ex, HttpStatus.BAD_REQUEST.getCode(),
                            new ErrorResponse(
                                    "Некорректный параметр запроса — 'year'",
                                    Collections.emptyList()));
                    return;
                }
                try {
                    yearFilter = Integer.parseInt(yearStr);
                } catch (NumberFormatException nfe) {
                    sendError(ex, HttpStatus.BAD_REQUEST.getCode(),
                            new ErrorResponse(
                                    "Некорректный параметр запроса — 'year'",
                                    Collections.singletonList("год должен быть числом")));
                    return;
                }
            }

            List<Movie> movies = service.listMovies(yearFilter);
            sendJsonWithGson(ex, HttpStatus.OK.getCode(), movies);

        } else {
            String idStr = subPath.substring(1);
            long id;
            try {
                id = Long.parseLong(idStr);
            } catch (NumberFormatException nfe) {
                sendError(ex, HttpStatus.BAD_REQUEST.getCode(),
                        new ErrorResponse("Некорректный ID", Collections.emptyList()));
                return;
            }

            Optional<Movie> opt = service.getById(id);
            if (opt.isPresent()) {
                sendJsonWithGson(ex, HttpStatus.OK.getCode(), opt.get());
            } else {
                sendError(ex, HttpStatus.NOT_FOUND.getCode(),
                        new ErrorResponse("Фильм не найден", Collections.emptyList()));
            }
        }
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        if (ct == null || !ct.toLowerCase(Locale.ROOT).contains("application/json")) {
            sendError(ex, HttpStatus.UNSUPPORTED_MEDIA_TYPE.getCode(),
                    new ErrorResponse("Не поддерживаемый тип медиа", Collections.emptyList()));
            return;
        }

        String body = readBody(ex.getRequestBody());
        Map<String, Object> payload;
        try {
            payload = gson.fromJson(body, new TypeToken<Map<String, Object>>() {
            }.getType());
        } catch (Exception jpe) {
            sendError(ex, HttpStatus.UNPROCESSABLE_ENTITY.getCode(),
                    new ErrorResponse("Ошибка валидации",
                            Collections.singletonList("Неверный формат JSON")));
            return;
        }

        List<String> errors = new ArrayList<>();
        String title = null;
        Integer year = null;


        if (payload.containsKey("title")) {
            Object tObj = payload.get("title");
            if (tObj instanceof String) {
                title = ((String) tObj).trim();
            } else {
                errors.add("название должно быть строкой");
            }
        } else {
            errors.add("поле 'title' отсутствует");
        }

        if (payload.containsKey("year")) {
            Object yObj = payload.get("year");
            if (yObj instanceof Number) {
                year = ((Number) yObj).intValue();
            } else if (yObj instanceof String) {
                try {
                    year = Integer.parseInt(((String) yObj));
                } catch (NumberFormatException nfe) {
                    errors.add("год должен быть числом");
                }
            } else {
                errors.add("год должен быть числом");
            }
        } else {
            errors.add("поле 'year' отсутствует");
        }

        if (!errors.isEmpty()) {
            sendError(ex, HttpStatus.UNPROCESSABLE_ENTITY.getCode(),
                    new ErrorResponse("Ошибка валидации", errors));
            return;
        }

        MovieService.Result<Movie> result = service.createMovie(title, year);

        if (!result.isSuccess()) {
            sendError(ex, HttpStatus.UNPROCESSABLE_ENTITY.getCode(),
                    result.getError());
            return;
        }

        Movie created = result.getData();
        ex.getResponseHeaders().add("Location", "/movies/" + created.getId());
        sendJsonWithGson(ex, HttpStatus.CREATED.getCode(), created);
    }

    private void handleDelete(HttpExchange ex, String subPath) throws IOException {
        if (subPath.isEmpty() || subPath.equals("/")) {
            sendError(ex, HttpStatus.BAD_REQUEST.getCode(),
                    new ErrorResponse("Некорректный ID", Collections.emptyList()));
            return;
        }

        String idStr = subPath.substring(1);
        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException nfe) {
            sendError(ex, HttpStatus.BAD_REQUEST.getCode(),
                    new ErrorResponse("Некорректный ID", Collections.emptyList()));
            return;
        }

        boolean deleted = service.delete(id);
        if (deleted) {
            sendNoContent(ex);
        } else {
            sendError(ex, HttpStatus.NOT_FOUND.getCode(),
                    new ErrorResponse("Фильм не найден", Collections.emptyList()));
        }
    }

    private Map<String, String> parseQuery(String query) {
        return Arrays.stream(query.split("&"))
                .map(s -> s.split("="))
                .filter(arr -> arr.length == 2)
                .collect(Collectors.toMap(
                        a -> URLDecoder.decode(a[0], StandardCharsets.UTF_8),
                        a -> URLDecoder.decode(a[1], StandardCharsets.UTF_8)));
    }

    private String readBody(InputStream is) {
        try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        }
    }

    /**
     * Отправляем JSON‑ответ, используя Gson.
     */
    private void sendJsonWithGson(HttpExchange ex, int status, Object data) throws IOException {
        String json = gson.toJson(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().add("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}

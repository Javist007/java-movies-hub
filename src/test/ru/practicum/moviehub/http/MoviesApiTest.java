package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.http.enums.HttpStatus;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для REST‑API, реализованного в {@link MoviesHandler} и {@link MoviesServer}.
 *
 * <p>Порядок действий:</p>
 * <ol>
 *   <li>В {@code @BeforeAll} создаём сервер на порту 8080.</li>
 *   <li>В {@code @AfterAll} останавливаем его.</li>
 *   <li>В каждом тесте очищаем хранилище (чтобы они были независимы).</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MoviesApiTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static final String MOVIES_HANDLER = "/movies";
    private MoviesServer server;
    private HttpClient client;
    private Gson gson;

    @BeforeAll
    void setUp() throws IOException {
        server = new MoviesServer(new MoviesStore(), 8080);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();

        client = HttpClient.newHttpClient();
        gson = new Gson();
    }

    @AfterAll
    void tearDown() {
        if (server != null) server.stop();
    }

    @BeforeEach
    void clearStore() {
        server.getStore().clear();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = get(MOVIES_HANDLER);
        assertStatus(resp, HttpStatus.OK);
        assertJsonResponse(resp);

        List<Movie> movies = parseBody(resp, new TypeToken<>() {
        });

        assertTrue(movies.isEmpty(), "Ожидается пустой список");
    }

    @Test
    void postMovie_success() throws Exception {
        MoviePayload payload = new MoviePayload("Inception", 2010);
        HttpResponse<String> resp = post(payload);

        assertStatus(resp, HttpStatus.CREATED);
        assertNotNull(resp.headers().firstValue("Location").orElse(null));
        assertJsonResponse(resp);

        Movie created = parseBody(resp, Movie.class);

        assertAll(
                () -> assertEquals("Inception", created.getTitle()),
                () -> assertEquals(2010, created.getYear())
        );
    }

    @Test
    void postMovie_emptyTitle_returns422() throws Exception {
        HttpResponse<String> resp = post(new MoviePayload("", 2010));
        assertStatus(resp, HttpStatus.UNPROCESSABLE_ENTITY);

        ErrorResponse err = parseBody(resp, ErrorResponse.class);

        assertEquals("Ошибка валидации", err.getError());
        assertTrue(err.getDetails().contains("название не должно быть пустым"));
    }

    @Test
    void postMovie_longTitle_returns422() throws Exception {
        String longTitle = "a".repeat(HttpStatus.SWITCHING_PROTOCOLS.getCode());
        HttpResponse<String> resp = post(new MoviePayload(longTitle, 2010));
        assertStatus(resp, HttpStatus.UNPROCESSABLE_ENTITY);

        ErrorResponse err = parseBody(resp, ErrorResponse.class);
        assertTrue(err.getDetails().contains("название не может превышать 100 символов"));
    }

    @Test
    void postMovie_yearTooLow_returns422() throws Exception {
        HttpResponse<String> resp = post(new MoviePayload("Test", 1887));
        assertStatus(resp, HttpStatus.UNPROCESSABLE_ENTITY);

        ErrorResponse err = parseBody(resp, ErrorResponse.class);

        int maxYear = LocalDate.now().getYear() + 1;
        String expected = String.format("год должен быть между 1888 и %d", maxYear);
        assertTrue(err.getDetails().contains(expected));
    }

    @Test
    void postMovie_yearTooHigh_returns422() throws Exception {
        int invalidYear = LocalDate.now().getYear() + 2;
        HttpResponse<String> resp = post(new MoviePayload("Future", invalidYear));
        assertStatus(resp, HttpStatus.UNPROCESSABLE_ENTITY);

        ErrorResponse err = parseBody(resp, ErrorResponse.class);

        int maxYear = LocalDate.now().getYear() + 1;
        String expected = String.format("год должен быть между 1888 и %d", maxYear);
        assertTrue(err.getDetails().contains(expected));
    }

    @Test
    void postMovie_wrongContentType_returns415() throws Exception {
        HttpResponse<String> resp = sendRequest("POST", BASE_URL + MOVIES_HANDLER,
                gson.toJson(new MoviePayload("Inception", 2010)),
                "text/plain");

        assertStatus(resp, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void postMovie_malformedJson_returns422() throws Exception {
        String body = "{\"title\":\"Inception\",\"year\":}";
        HttpResponse<String> resp = sendRequest("POST", BASE_URL + MOVIES_HANDLER,
                body, "application/json");

        assertStatus(resp, HttpStatus.UNPROCESSABLE_ENTITY);
        ErrorResponse err = parseBody(resp, ErrorResponse.class);
        assertTrue(err.getDetails().contains("Неверный формат JSON"));
    }

    @Test
    void getMovie_byId_success() throws Exception {
        MoviePayload payload = new MoviePayload("Inception", 2010);
        HttpResponse<String> postResp = post(payload);
        Movie created = parseBody(postResp, Movie.class);

        HttpResponse<String> resp = get("/movies/" + created.getId());
        assertStatus(resp, HttpStatus.OK);

        Movie fetched = parseBody(resp, Movie.class);
        assertAll(
                () -> assertEquals(created.getTitle(), fetched.getTitle()),
                () -> assertEquals(created.getYear(), fetched.getYear())
        );
    }

    @Test
    void getMovie_notFound_returns404() throws Exception {
        HttpResponse<String> resp = get(MOVIES_HANDLER + "/9999");
        assertStatus(resp, HttpStatus.NOT_FOUND);
    }

    @Test
    void getMovie_nonNumericId_returns400() throws Exception {
        HttpResponse<String> resp = get(MOVIES_HANDLER + "/abc");
        assertStatus(resp, HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteMovie_success() throws Exception {
        MoviePayload payload = new MoviePayload("Inception", 2010);
        Movie created = parseBody(post(payload), Movie.class);

        HttpResponse<String> delResp = delete("/movies/" + created.getId());
        assertStatus(delResp, HttpStatus.NO_CONTENT);

        HttpResponse<String> getResp = get("/movies/" + created.getId());
        assertStatus(getResp, HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteMovie_notFound_returns404() throws Exception {
        HttpResponse<String> resp = delete(MOVIES_HANDLER + "/9999");
        assertStatus(resp, HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteMovie_nonNumericId_returns400() throws Exception {
        HttpResponse<String> resp = delete(MOVIES_HANDLER + "/abc");
        assertStatus(resp, HttpStatus.BAD_REQUEST);
    }

    @Test
    void getMovies_filterByYear_returnsMatching() throws Exception {
        post(new MoviePayload("Movie1", 2010));
        post(new MoviePayload("Movie2", 2015));
        post(new MoviePayload("Movie3", 2010));

        HttpResponse<String> resp = get(MOVIES_HANDLER + "?year=2010");
        assertStatus(resp, HttpStatus.OK);

        List<Movie> movies = parseBody(resp, new TypeToken<>() {
        });
        assertEquals(2, movies.size());
    }

    @Test
    void getMovies_filterByYear_noMatches_returnsEmpty() throws Exception {
        post(new MoviePayload("Movie1", 2005));

        HttpResponse<String> resp = get(MOVIES_HANDLER + "?year=2010");
        assertStatus(resp, HttpStatus.OK);

        List<Movie> movies = parseBody(resp, new TypeToken<>() {
        });
        assertTrue(movies.isEmpty(), "Ожидается пустой список при отсутствии совпадений");
    }

    @Test
    void getMovies_invalidYearQuery_returns400() throws Exception {
        HttpResponse<String> resp = get(MOVIES_HANDLER + "?year=abcd");
        assertStatus(resp, HttpStatus.BAD_REQUEST);
    }

    /**
     * Вспомогательные методы.
     */
    private HttpResponse<String> sendRequest(String method, String uri,
                                             String body, String contentType)
            throws IOException, InterruptedException {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uri));

        switch (method.toUpperCase()) {
            case "GET" -> builder.GET();
            case "POST" -> {
                if (body != null) {
                    builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                } else {
                    builder.POST(HttpRequest.BodyPublishers.noBody());
                }
                if (contentType != null)
                    builder.header("Content-Type", contentType);
            }
            case "DELETE" -> builder.DELETE();
            default -> throw new IllegalArgumentException("Неподдерживаемый метод: " + method);
        }

        HttpRequest request = builder.build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String uri) throws IOException, InterruptedException {
        return sendRequest("GET", BASE_URL + uri, null, null);
    }

    private HttpResponse<String> post(Object bodyObj) throws IOException, InterruptedException {
        String body = gson.toJson(bodyObj);
        return sendRequest("POST", BASE_URL + MOVIES_HANDLER, body, "application/json");
    }

    private HttpResponse<String> delete(String uri) throws IOException, InterruptedException {
        return sendRequest("DELETE", BASE_URL + uri, null, null);
    }

    private <T> T parseBody(HttpResponse<String> resp, Class<T> clazz) {
        return gson.fromJson(resp.body(), clazz);
    }

    private <T> T parseBody(HttpResponse<String> resp, TypeToken<T> type) {
        return gson.fromJson(resp.body(), type.getType());
    }

    private void assertStatus(HttpResponse<?> resp, HttpStatus expected) {
        assertEquals(expected.getCode(), resp.statusCode(),
                () -> "Ожидаемый статус %d но получен %d".formatted(expected.getCode(), resp.statusCode()));
    }

    private void assertJsonResponse(HttpResponse<?> resp) {
        assertTrue(resp.headers().firstValue("Content-Type")
                        .orElse("").contains("application/json"),
                "Ожидаемый ответ в формате JSON");
    }

    private static final class MoviePayload {
        String title;
        int year;

        MoviePayload(String title, int year) {
            this.title = title;
            this.year = year;
        }
    }
}

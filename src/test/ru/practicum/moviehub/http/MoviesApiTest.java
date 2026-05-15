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
import java.net.http.HttpResponse.BodyHandlers;
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

    private HttpResponse<String> sendRequest(String method, String uri,
                                             String body, String contentType)
            throws IOException, InterruptedException {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uri));

        switch (method.toUpperCase()) {
            case "GET":
                builder.GET();
                break;
            case "POST":
                if (body != null) {
                    builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                } else {
                    builder.POST(HttpRequest.BodyPublishers.noBody());
                }
                if (contentType != null)
                    builder.header("Content-Type", contentType);
                break;
            case "DELETE":
                builder.DELETE();
                break;
            default:
                throw new IllegalArgumentException("Неподдерживаемый метод: " + method);
        }

        HttpRequest request = builder.build();
        return client.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
    }


    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpResponse<String> resp = sendRequest("GET", BASE_URL + "/movies", null, null);
        assertEquals(HttpStatus.OK.getCode(), resp.statusCode());
        assertTrue(resp.headers().firstValue("Content-Type")
                .orElse("").contains("application/json"));

        List<Movie> movies =
                gson.fromJson(resp.body(),
                        new TypeToken<List<Movie>>() {
                        }.getType());

        assertNotNull(movies);
        assertTrue(movies.isEmpty(), "Ожидается пустой список");
    }

    @Test
    void postMovie_success() throws Exception {
        String body = "{\"title\":\"Inception\",\"year\":2010}";
        HttpResponse<String> resp = sendRequest("POST", BASE_URL + "/movies",
                body, "application/json");

        assertEquals(HttpStatus.CREATED.getCode(), resp.statusCode());
        assertNotNull(resp.headers().firstValue("Location"));
        assertTrue(resp.headers().firstValue("Content-Type")
                .orElse("").contains("application/json"));

        Movie created =
                gson.fromJson(resp.body(), Movie.class);

        assertEquals("Inception", created.getTitle());
        assertEquals(2010, created.getYear());
    }

    @Test
    void postMovie_emptyTitle_returns422() throws Exception {
        String body = "{\"title\":\"\",\"year\":2010}";
        HttpResponse<String> resp = sendRequest("POST", BASE_URL + "/movies",
                body, "application/json");

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.getCode(), resp.statusCode());

        ErrorResponse err =
                gson.fromJson(resp.body(), ErrorResponse.class);

        assertEquals("Ошибка валидации", err.getError());
        assertTrue(err.getDetails().contains("название не должно быть пустым"));
    }

    @Test
    void postMovie_longTitle_returns422() throws Exception {
        String longTitle = "a".repeat(HttpStatus.SWITCHING_PROTOCOLS.getCode());
        String body = "{\"title\":\"" + longTitle + "\",\"year\":2010}";
        HttpResponse<String> resp = sendRequest("POST", BASE_URL + "/movies",
                body, "application/json");

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.getCode(), resp.statusCode());
        ErrorResponse err =
                gson.fromJson(resp.body(), ErrorResponse.class);
        assertTrue(err.getDetails().contains("название не может превышать 100 символов"));
    }

    @Test
    void postMovie_yearTooLow_returns422() throws Exception {
        String body = "{\"title\":\"Test\",\"year\":1887}";
        HttpResponse<String> resp = sendRequest("POST", BASE_URL + "/movies",
                body, "application/json");

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.getCode(), resp.statusCode());
        ErrorResponse err =
                gson.fromJson(resp.body(), ErrorResponse.class);

        int maxYear = LocalDate.now().getYear() + 1;
        String expected = String.format("год должен быть между 1888 и %d", maxYear);
        assertTrue(err.getDetails().contains(expected));
    }

    @Test
    void postMovie_yearTooHigh_returns422() throws Exception {
        int invalidYear = LocalDate.now().getYear() + 2;
        String body = "{\"title\":\"Future\",\"year\":" + invalidYear + "}";
        HttpResponse<String> resp = sendRequest("POST", BASE_URL + "/movies",
                body, "application/json");

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.getCode(), resp.statusCode());
        ErrorResponse err =
                gson.fromJson(resp.body(), ErrorResponse.class);

        int maxYear = LocalDate.now().getYear() + 1;
        String expected = String.format("год должен быть между 1888 и %d", maxYear);
        assertTrue(err.getDetails().contains(expected));
    }

    @Test
    void postMovie_wrongContentType_returns415() throws Exception {
        String body = "{\"title\":\"Inception\",\"year\":2010}";
        HttpResponse<String> resp = sendRequest("POST", BASE_URL + "/movies",
                body, "text/plain");

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE.getCode(), resp.statusCode());
    }

    @Test
    void postMovie_malformedJson_returns422() throws Exception {
        String body = "{\"title\":\"Inception\",\"year\":}";
        HttpResponse<String> resp = sendRequest("POST", BASE_URL + "/movies",
                body, "application/json");

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.getCode(), resp.statusCode());
        ErrorResponse err =
                gson.fromJson(resp.body(), ErrorResponse.class);
        assertTrue(err.getDetails().contains("Неверный формат JSON"));
    }

    @Test
    void getMovie_byId_success() throws Exception {
        String body = "{\"title\":\"Inception\",\"year\":2010}";
        HttpResponse<String> postResp = sendRequest("POST", BASE_URL + "/movies",
                body, "application/json");
        Movie created =
                gson.fromJson(postResp.body(), Movie.class);

        HttpResponse<String> getResp = sendRequest("GET", BASE_URL + "/movies/" + created.getId(),
                null, null);
        assertEquals(HttpStatus.OK.getCode(), getResp.statusCode());

        Movie fetched =
                gson.fromJson(getResp.body(), Movie.class);

        assertEquals(created.getTitle(), fetched.getTitle());
        assertEquals(created.getYear(), fetched.getYear());
    }

    @Test
    void getMovie_notFound_returns404() throws Exception {
        HttpResponse<String> resp = sendRequest("GET", BASE_URL + "/movies/9999",
                null, null);
        assertEquals(HttpStatus.NOT_FOUND.getCode(), resp.statusCode());
    }

    @Test
    void getMovie_nonNumericId_returns400() throws Exception {
        HttpResponse<String> resp = sendRequest("GET", BASE_URL + "/movies/abc",
                null, null);
        assertEquals(HttpStatus.BAD_REQUEST.getCode(), resp.statusCode());
    }

    @Test
    void deleteMovie_success() throws Exception {
        String body = "{\"title\":\"Inception\",\"year\":2010}";
        HttpResponse<String> postResp = sendRequest("POST", BASE_URL + "/movies",
                body, "application/json");
        Movie created =
                gson.fromJson(postResp.body(), Movie.class);

        HttpResponse<String> delResp = sendRequest("DELETE", BASE_URL + "/movies/" + created.getId(),
                null, null);
        assertEquals(HttpStatus.NO_CONTENT.getCode(), delResp.statusCode());

        HttpResponse<String> getResp = sendRequest("GET", BASE_URL + "/movies/" + created.getId(),
                null, null);
        assertEquals(HttpStatus.NOT_FOUND.getCode(), getResp.statusCode());
    }

    @Test
    void deleteMovie_notFound_returns404() throws Exception {
        HttpResponse<String> resp = sendRequest("DELETE", BASE_URL + "/movies/9999",
                null, null);
        assertEquals(HttpStatus.NOT_FOUND.getCode(), resp.statusCode());
    }

    @Test
    void deleteMovie_nonNumericId_returns400() throws Exception {
        HttpResponse<String> resp = sendRequest("DELETE", BASE_URL + "/movies/abc",
                null, null);
        assertEquals(HttpStatus.BAD_REQUEST.getCode(), resp.statusCode());
    }

    @Test
    void getMovies_filterByYear_returnsMatching() throws Exception {
        sendRequest("POST", BASE_URL + "/movies",
                "{\"title\":\"Movie1\",\"year\":2010}", "application/json");
        sendRequest("POST", BASE_URL + "/movies",
                "{\"title\":\"Movie2\",\"year\":2015}", "application/json");
        sendRequest("POST", BASE_URL + "/movies",
                "{\"title\":\"Movie3\",\"year\":2010}", "application/json");

        HttpResponse<String> resp = sendRequest("GET", BASE_URL + "/movies?year=2010",
                null, null);
        assertEquals(HttpStatus.OK.getCode(), resp.statusCode());

        List<Movie> movies =
                gson.fromJson(resp.body(),
                        new TypeToken<List<Movie>>() {
                        }.getType());

        assertEquals(2, movies.size());
    }

    @Test
    void getMovies_filterByYear_noMatches_returnsEmpty() throws Exception {
        sendRequest("POST", BASE_URL + "/movies",
                "{\"title\":\"Movie1\",\"year\":2005}", "application/json");

        HttpResponse<String> resp = sendRequest("GET", BASE_URL + "/movies?year=2010",
                null, null);
        assertEquals(HttpStatus.OK.getCode(), resp.statusCode());

        List<Movie> movies =
                gson.fromJson(resp.body(),
                        new TypeToken<List<Movie>>() {
                        }.getType());
        assertTrue(movies.isEmpty(), "Ожидается пустой список при отсутствии совпадений");
    }

    @Test
    void getMovies_invalidYearQuery_returns400() throws Exception {
        HttpResponse<String> resp = sendRequest("GET", BASE_URL + "/movies?year=abcd",
                null, null);
        assertEquals(HttpStatus.BAD_REQUEST.getCode(), resp.statusCode());
    }

}

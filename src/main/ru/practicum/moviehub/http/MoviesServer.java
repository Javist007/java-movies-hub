package ru.practicum.moviehub.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;

/**
 * Запускает HTTP‑сервер, создаёт контекст /movies и привязывает обработчик.
 */
public class MoviesServer {
    private final MoviesStore store;
    private final HttpServer httpServer;

    public MoviesServer(MoviesStore store, int port) throws IOException {
        this.store = store;
        MoviesHandler handler = new MoviesHandler(store);
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/movies", handler);
    }

    /** Запускает сервер. */
    public void start() { httpServer.start(); }

    /** Останавливает сервер. */
    public void stop() { httpServer.stop(0); }

    /**
     * Для тестов – очищает все данные.
     *
     * @return ссылка на хранилище
     */
    public MoviesStore getStore() {
        return store;
    }
}

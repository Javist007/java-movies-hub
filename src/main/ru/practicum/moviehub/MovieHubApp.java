package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;

public class MovieHubApp {
    public static void main(String[] args) {
        try {
            final MoviesServer server = new MoviesServer(new MoviesStore(), 8080);
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            server.start();
        } catch (IOException e) {
            System.err.println("Не удалось запустить сервер: " + e.getMessage());
            System.exit(1);
        }
    }
}
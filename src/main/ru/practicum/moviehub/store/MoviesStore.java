package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Простое хранилище фильмов.
 */
public class MoviesStore {
    private final ConcurrentHashMap<Long, Movie> movies = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    /**
     * Возвращает список всех фильмов.
     */
    public List<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }

    /**
     * Находит фильм по идентификатору, если он существует.
     */
    public Optional<Movie> getById(long id) {
        return Optional.ofNullable(movies.get(id));
    }

    /**
     * Добавляет новый фильм в хранилище.
     */
    public Movie add(String title, int year) {
        long id = idGenerator.getAndIncrement();
        Movie movie = new Movie(id, title, year);
        movies.put(id, movie);
        return movie;
    }

    /**
     * Удаляет фильм по идентификатору. Возвращает true, если объект удалён.
     */
    public boolean delete(long id) {
        return movies.remove(id) != null;
    }

    /**
     * Очищает хранилище.
     */
    public void clear() {
        movies.clear();
        idGenerator.set(1);
    }
}

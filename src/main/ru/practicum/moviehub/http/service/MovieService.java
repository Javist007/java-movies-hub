package ru.practicum.moviehub.http.service;

import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис бизнес‑логики работы с хранилищем фильмов.
 */
public class MovieService {

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MIN_YEAR = 1888;

    private final MoviesStore store;

    public MovieService(MoviesStore store) {
        this.store = store;
    }

    /**
     * Создаёт новый фильм после проверки входных данных.
     */
    public Result<Movie> createMovie(String title, Integer year) {
        List<String> errors = validate(title, year);
        if (!errors.isEmpty()) {
            return Result.failure(new ErrorResponse("Ошибка валидации", errors));
        }
        Movie movie = store.add(title, year);
        return Result.success(movie);
    }

    /**
     * Возвращает список фильмов с возможной фильтрацией по году.
     */
    public List<Movie> listMovies(Integer yearFilter) {
        List<Movie> movies = store.getAll();

        if (yearFilter == null) {
            return movies;
        }
        int filterYear = yearFilter;
        return movies.stream()
                .filter(m -> m.getYear() == filterYear)
                .collect(Collectors.toList());
    }

    /**
     * Получает фильм по id.
     */
    public Optional<Movie> getById(long id) {
        return store.getById(id);
    }

    /**
     * Удаляет фильм. Возвращает true, если удалён.
     */
    public boolean delete(long id) {
        return store.delete(id);
    }

    /**
     * Валидирует данные фильма.
     *
     * @param title название
     * @param year  год выпуска
     * @return список ошибок (пустой, если данных корректно)
     */
    private List<String> validate(String title, int year) {
        List<String> errors = new ArrayList<>();

        if (title == null || title.trim().isEmpty()) {
            errors.add("название не должно быть пустым");
        } else if (title.length() > MAX_TITLE_LENGTH) {
            errors.add("название не может превышать 100 символов");
        }

        int currentYear = LocalDate.now().getYear();
        if (year < MIN_YEAR || year > currentYear + 1) {
            errors.add(String.format("год должен быть между 1888 и %d", currentYear + 1));
        }

        return errors;
    }

    /**
     * Внутренний класс‑обёртка для результата операции.
     */
    public static class Result<T> {
        private final T data;
        private final ErrorResponse error;

        private Result(T data, ErrorResponse error) {
            this.data = data;
            this.error = error;
        }

        /**
         * Успешный результат.
         */
        public static <T> Result<T> success(T data) {
            return new Result<>(data, null);
        }

        /**
         * Неудачный результат с ошибкой.
         */
        public static <T> Result<T> failure(ErrorResponse error) {
            return new Result<>(null, error);
        }

        public boolean isSuccess() {
            return error == null;
        }

        public T getData() {
            return data;
        }

        public ErrorResponse getError() {
            return error;
        }
    }
}


package com.example.apiclient;

import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class ConsoleTaskApp {
    private static final int PAGE_SIZE = 20;

    private final Scanner input = new Scanner(System.in);
    private final TaskApiClient api = new TaskApiClient(ApiConfiguration.baseUri());

    private ConsoleTaskApp() {
    }

    public static void run() {
        new ConsoleTaskApp().runMenu();
    }

    private void runMenu() {
        System.out.println("Task Flow — терминальный трекер задач");
        System.out.println("REST API: " + api.baseUrl());
        boolean running = true;
        while (running) {
            printMenu();
            try {
                String choice = prompt("Выберите действие");
                switch (choice) {
                    case "1" -> browseTasks();
                    case "2" -> createTask();
                    case "3" -> editTask();
                    case "4" -> changeTaskStatus();
                    case "5" -> deleteTask();
                    case "0" -> running = false;
                    default -> System.out.println("Неизвестная команда.");
                }
            } catch (NoSuchElementException exception) {
                running = false;
            } catch (RuntimeException exception) {
                System.out.println("Ошибка: " + errorMessage(exception));
            }
            System.out.println();
        }
        System.out.println("Работа завершена.");
    }

    private void printMenu() {
        System.out.println("""

                1. Список задач / поиск / фильтр
                2. Создать задачу
                3. Изменить задачу
                4. Изменить статус
                5. Удалить задачу
                0. Выход""");
    }

    private void browseTasks() {
        String query = prompt("Поиск по названию/описанию (Enter — без поиска)");
        TaskStatus status = readStatus("Фильтр по статусу: 1 — новая, 2 — в работе, 3 — завершена, Enter — все");
        int page = 0;
        while (true) {
            PagedTasks result = await(api.listTasks(page, PAGE_SIZE, status, query));
            printTasks(result);
            if (result.totalPages() <= 1) {
                return;
            }
            String navigation = prompt("n — следующая, p — предыдущая, Enter — назад в меню");
            if ("n".equalsIgnoreCase(navigation) && result.hasNext()) {
                page++;
            } else if ("p".equalsIgnoreCase(navigation) && page > 0) {
                page--;
            } else if (navigation.isBlank()) {
                return;
            } else {
                System.out.println("Такой переход недоступен.");
            }
        }
    }

    private void printTasks(PagedTasks result) {
        if (result.content().isEmpty()) {
            System.out.println("Задач не найдено.");
        } else {
            System.out.printf("%nЗадачи — страница %d из %d (всего: %d)%n",
                    result.page() + 1, result.totalPages(), result.totalElements());
            for (Task task : result.content()) {
                System.out.printf("%n#%d | %s | %s%n%s%nСоздана: %s%n",
                        task.id(), task.status().label(), task.title(),
                        task.description() == null || task.description().isBlank()
                                ? "Описание не добавлено" : task.description(),
                        Task.formatCreatedAt(task.createdAt()));
            }
        }
    }

    private void createTask() {
        String title = readRequired("Название (до 100 символов)", 100);
        String description = readLimited("Описание (до 2000 символов; Enter — пропустить)", 2_000);
        TaskStatus status = readStatus("Статус: 1 — новая, 2 — в работе, 3 — завершена, Enter — новая");
        Task created = await(api.createTask(new TaskInput(
                title, description.isBlank() ? null : description,
                status == null ? TaskStatus.NEW : status)));
        System.out.println("Создана задача #" + created.id() + ": " + created.title());
    }

    private void editTask() {
        Task task = findTask(readId());
        System.out.println("Enter — оставить текущее значение; для очистки описания введите !clear.");
        String titleInput = readLimited("Название [" + task.title() + "]", 100);
        String descriptionInput = readLimited("Описание [" + displayDescription(task.description()) + "]", 2_000);
        TaskStatus statusInput = readStatus(
                "Статус [" + task.status().label() + "]: 1 — новая, 2 — в работе, 3 — завершена, Enter — без изменений");

        String title = titleInput.isBlank() ? task.title() : titleInput;
        String description = descriptionInput.isBlank() ? task.description()
                : "!clear".equals(descriptionInput) ? "" : descriptionInput;
        TaskStatus status = statusInput == null ? task.status() : statusInput;
        Task updated = await(api.updateTask(task.id(), new TaskInput(title, description, status)));
        System.out.println("Изменения задачи #" + updated.id() + " сохранены.");
    }

    private void changeTaskStatus() {
        Task task = findTask(readId());
        TaskStatus status = readStatus("Новый статус: 1 — новая, 2 — в работе, 3 — завершена");
        if (status == null) {
            System.out.println("Статус не изменён.");
            return;
        }
        Task updated = await(api.updateTask(task.id(),
                new TaskInput(task.title(), task.description(), status)));
        System.out.println("Статус задачи #" + updated.id() + ": " + updated.status().label());
    }

    private void deleteTask() {
        Task task = findTask(readId());
        String confirmation = prompt("Удалить задачу \"" + task.title() + "\"? (y/N)");
        if ("y".equalsIgnoreCase(confirmation) || "д".equalsIgnoreCase(confirmation)) {
            await(api.deleteTask(task.id()));
            System.out.println("Задача #" + task.id() + " удалена.");
        } else {
            System.out.println("Удаление отменено.");
        }
    }

    private Task findTask(long id) {
        return await(api.getTask(id));
    }

    private long readId() {
        while (true) {
            String value = prompt("ID задачи");
            try {
                long id = Long.parseLong(value);
                if (id > 0) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
                // Ask again when the entered ID is not a positive integer.
            }
            System.out.println("Введите положительный числовой ID.");
        }
    }

    private TaskStatus readStatus(String message) {
        while (true) {
            String value = prompt(message);
            if (value.isBlank()) {
                return null;
            }
            switch (value) {
                case "1", "NEW" -> {
                    return TaskStatus.NEW;
                }
                case "2", "IN_PROGRESS" -> {
                    return TaskStatus.IN_PROGRESS;
                }
                case "3", "DONE" -> {
                    return TaskStatus.DONE;
                }
                default -> System.out.println("Введите 1, 2, 3 или оставьте поле пустым.");
            }
        }
    }

    private String readRequired(String message, int maxLength) {
        while (true) {
            String value = readLimited(message, maxLength).trim();
            if (!value.isEmpty()) {
                return value;
            }
            System.out.println("Название не может быть пустым.");
        }
    }

    private String readLimited(String message, int maxLength) {
        while (true) {
            String value = prompt(message);
            if (value.length() <= maxLength) {
                return value;
            }
            System.out.println("Максимальная длина: " + maxLength + " символов.");
        }
    }

    private String prompt(String message) {
        System.out.print(message + ": ");
        return input.nextLine().trim();
    }

    private String displayDescription(String description) {
        return description == null || description.isBlank() ? "пусто" : description;
    }

    private String errorMessage(Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "неизвестная ошибка" : cause.getMessage();
    }

    private static <T> T await(CompletableFuture<T> request) {
        return request.join();
    }
}

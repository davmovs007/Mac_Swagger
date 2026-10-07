package com.example.apiclient;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class TaskTrackerApplication extends Application {
    private static final int PAGE_SIZE = 100;
    private final TaskApiClient api = new TaskApiClient(ApiConfiguration.baseUri());
    private final BorderPane root = new BorderPane();
    private final VBox board = new VBox(12);
    private final Label connectionLabel = new Label("Подключение...");
    private final Label feedbackLabel = new Label();
    private final Label resultLabel = new Label("Загрузка задач...");
    private final Label pageLabel = new Label("Страница —");
    private final Label totalMetric = new Label("—");
    private final Label progressMetric = new Label("—");
    private final Label doneMetric = new Label("—");
    private final TextField searchField = new TextField();
    private final Button previousButton = new Button("← Назад");
    private final Button nextButton = new Button("Вперёд →");
    private final ToggleGroup statusFilters = new ToggleGroup();
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(350));
    private TaskStatus selectedStatus;
    private int page;
    private long latestLoad;

    @Override
    public void start(Stage stage) {
        root.setLeft(createSidebar());
        root.setCenter(createWorkspace());

        Scene scene = new Scene(root, 1240, 820);
        var stylesheet = TaskTrackerApplication.class.getResource("app.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        stage.setTitle("Task Flow — управление задачами");
        stage.setScene(scene);
        stage.setMinWidth(940);
        stage.setMinHeight(650);
        stage.show();
        loadTasks();
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(26, 16, 20, 16));
        sidebar.setPrefWidth(230);
        sidebar.setMinWidth(210);

        Label brand = new Label("◆  Task Flow");
        brand.getStyleClass().add("brand");
        Label subtitle = new Label("ТРЕКЕР ЗАДАЧ");
        subtitle.getStyleClass().add("sidebar-caption");
        VBox.setMargin(subtitle, new Insets(2, 0, 24, 4));

        Label filtersLabel = new Label("РАБОЧИЙ ПРОЦЕСС");
        filtersLabel.getStyleClass().add("sidebar-caption");
        VBox filters = new VBox(6);
        addStatusFilter(filters, "Все задачи", null, true);
        addStatusFilter(filters, TaskStatus.NEW.label(), TaskStatus.NEW, false);
        addStatusFilter(filters, TaskStatus.IN_PROGRESS.label(), TaskStatus.IN_PROGRESS, false);
        addStatusFilter(filters, TaskStatus.DONE.label(), TaskStatus.DONE, false);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Separator separator = new Separator();
        Label apiCaption = new Label("ЛОКАЛЬНЫЙ API");
        apiCaption.getStyleClass().add("sidebar-caption");
        Label apiAddress = new Label(api.baseUrl().toString());
        apiAddress.getStyleClass().add("api-address");
        apiAddress.setWrapText(true);
        connectionLabel.getStyleClass().add("connection-status");

        sidebar.getChildren().addAll(brand, subtitle, filtersLabel, filters, spacer, separator,
                apiCaption, apiAddress, connectionLabel);
        return sidebar;
    }

    private void addStatusFilter(VBox container, String text, TaskStatus status, boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setToggleGroup(statusFilters);
        button.getStyleClass().add("filter-button");
        button.setUserData(status);
        if (selected) {
            button.setSelected(true);
        }
        container.getChildren().add(button);
        button.selectedProperty().addListener((observable, wasSelected, isSelected) -> {
            if (isSelected) {
                selectedStatus = status;
                page = 0;
                loadTasks();
            }
        });
    }

    private VBox createWorkspace() {
        VBox workspace = new VBox(22);
        workspace.getStyleClass().add("workspace");
        workspace.setPadding(new Insets(32, 34, 28, 34));

        Label eyebrow = new Label("ПРОЕКТНЫЙ ТРЕКЕР");
        eyebrow.getStyleClass().add("eyebrow");
        Label heading = new Label("Доска задач");
        heading.getStyleClass().add("page-title");
        Label description = new Label("Планируйте работу, следите за ходом выполнения и отмечайте готовое.");
        description.getStyleClass().add("muted-text");
        VBox titleBlock = new VBox(5, eyebrow, heading, description);

        Button createButton = new Button("+  Новая задача");
        createButton.getStyleClass().add("primary-button");
        createButton.setOnAction(event -> openTaskDialog(null));
        HBox header = new HBox(18, titleBlock, spacer(), createButton);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox metrics = new HBox(12,
                metricCard("Найдено задач", totalMetric, "в текущем запросе", "metric-violet"),
                metricCard("В работе", progressMetric, "на этой странице", "metric-amber"),
                metricCard("Завершено", doneMetric, "на этой странице", "metric-green"));

        searchField.setPromptText("Поиск по названию и описанию");
        searchField.getStyleClass().add("search-field");
        searchField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= 100 ? change : null));
        searchField.setOnAction(event -> {
            searchDelay.stop();
            page = 0;
            loadTasks();
        });
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            searchDelay.stop();
            searchDelay.setOnFinished(event -> {
                page = 0;
                loadTasks();
            });
            searchDelay.playFromStart();
        });

        Button refreshButton = new Button("↻  Обновить");
        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setOnAction(event -> loadTasks());
        previousButton.getStyleClass().add("secondary-button");
        nextButton.getStyleClass().add("secondary-button");
        previousButton.setOnAction(event -> {
            if (page > 0) {
                page--;
                loadTasks();
            }
        });
        nextButton.setOnAction(event -> {
            page++;
            loadTasks();
        });
        HBox toolbar = new HBox(10, searchField, refreshButton, spacer(), previousButton, pageLabel, nextButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(toolbar, new Insets(4, 0, 0, 0));

        feedbackLabel.getStyleClass().add("feedback");
        feedbackLabel.setWrapText(true);
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);

        resultLabel.getStyleClass().add("result-label");
        HBox resultSummary = new HBox(resultLabel, spacer());
        resultSummary.setAlignment(Pos.CENTER_LEFT);
        board.getStyleClass().add("board");
        board.setFillWidth(true);
        VBox.setVgrow(board, Priority.ALWAYS);

        workspace.getChildren().addAll(header, metrics, toolbar, feedbackLabel, resultSummary, board);
        return workspace;
    }

    private VBox metricCard(String title, Label value, String note, String styleClass) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-title");
        value.getStyleClass().addAll("metric-value", styleClass);
        Label noteLabel = new Label(note);
        noteLabel.getStyleClass().add("metric-note");
        VBox card = new VBox(7, titleLabel, value, noteLabel);
        card.getStyleClass().add("metric-card");
        card.setPadding(new Insets(15, 18, 14, 18));
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private void loadTasks() {
        long requestId = ++latestLoad;
        setConnection("Подключение...", false);
        api.listTasks(page, PAGE_SIZE, selectedStatus, searchField.getText())
                .whenComplete((response, error) -> Platform.runLater(() -> {
                    if (requestId != latestLoad) {
                        return;
                    }
                    if (error != null) {
                        setConnection("API недоступен", false);
                        showFeedback("Не удалось загрузить задачи: " + errorMessage(error));
                        resultLabel.setText("Не удалось загрузить задачи");
                        totalMetric.setText("—");
                        progressMetric.setText("—");
                        doneMetric.setText("—");
                        updateBoard(List.of());
                        updatePagination(null);
                        return;
                    }

                    if (response.content().isEmpty() && page > 0 && page >= response.totalPages()) {
                        page = Math.max(0, response.totalPages() - 1);
                        loadTasks();
                        return;
                    }

                    setConnection("API доступен", true);
                    hideFeedback();
                    totalMetric.setText(Long.toString(response.totalElements()));
                    long inProgress = response.content().stream()
                            .filter(task -> task.status() == TaskStatus.IN_PROGRESS).count();
                    long done = response.content().stream()
                            .filter(task -> task.status() == TaskStatus.DONE).count();
                    progressMetric.setText(Long.toString(inProgress));
                    doneMetric.setText(Long.toString(done));
                    resultLabel.setText(response.totalElements() == 0
                            ? "По заданным условиям задач нет"
                            : "Показано " + response.content().size() + " из " + response.totalElements() + " задач");
                    updateBoard(response.content());
                    updatePagination(response);
                }));
    }

    private void updateBoard(List<Task> tasks) {
        board.getChildren().clear();
        HBox columns = new HBox(14);
        for (TaskStatus status : TaskStatus.values()) {
            List<Task> matching = tasks.stream().filter(task -> task.status() == status).toList();
            VBox column = new VBox(10);
            column.getStyleClass().add("task-column");
            column.setPadding(new Insets(13));
            HBox heading = new HBox(8);
            heading.setAlignment(Pos.CENTER_LEFT);
            Label statusDot = new Label("●");
            statusDot.getStyleClass().add("status-dot-" + status.name().toLowerCase());
            Label name = new Label(status.label());
            name.getStyleClass().add("column-title");
            Label count = new Label(Integer.toString(matching.size()));
            count.getStyleClass().add("count-badge");
            heading.getChildren().addAll(statusDot, name, spacer(), count);

            VBox cards = new VBox(10);
            cards.setFillWidth(true);
            for (Task task : matching) {
                cards.getChildren().add(createTaskCard(task));
            }
            if (matching.isEmpty()) {
                Label empty = new Label("Здесь пока нет задач");
                empty.getStyleClass().add("empty-column");
                empty.setWrapText(true);
                cards.getChildren().add(empty);
            }
            ScrollPane scrollPane = new ScrollPane(cards);
            scrollPane.setFitToWidth(true);
            scrollPane.getStyleClass().add("column-scroll");
            VBox.setVgrow(scrollPane, Priority.ALWAYS);
            column.getChildren().addAll(heading, scrollPane);
            HBox.setHgrow(column, Priority.ALWAYS);
            column.setMinWidth(0);
            column.setPrefWidth(300);
            columns.getChildren().add(column);
        }
        board.getChildren().add(columns);
        VBox.setVgrow(columns, Priority.ALWAYS);
    }

    private VBox createTaskCard(Task task) {
        VBox card = new VBox(10);
        card.getStyleClass().add("task-card");
        card.setPadding(new Insets(14));

        Label taskId = new Label("#" + task.id());
        taskId.getStyleClass().add("task-id");
        Button editButton = new Button("Изменить");
        editButton.getStyleClass().add("text-button");
        editButton.setOnAction(event -> openTaskDialog(task));
        HBox top = new HBox(taskId, spacer(), editButton);
        top.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(task.title());
        title.getStyleClass().add("task-title");
        title.setWrapText(true);
        Label description = new Label(task.description() == null || task.description().isBlank()
                ? "Описание не добавлено" : task.description());
        description.getStyleClass().add("task-description");
        description.setWrapText(true);
        description.setMaxHeight(78);

        ComboBox<TaskStatus> statusPicker = new ComboBox<>(FXCollections.observableArrayList(TaskStatus.values()));
        statusPicker.setValue(task.status());
        statusPicker.setMaxWidth(Double.MAX_VALUE);
        statusPicker.getStyleClass().add("status-picker");
        statusPicker.setOnAction(event -> {
            TaskStatus newStatus = statusPicker.getValue();
            if (newStatus != null && newStatus != task.status()) {
                updateTaskStatus(task, newStatus);
            }
        });
        Label createdAt = new Label("Создана: " + Task.formatCreatedAt(task.createdAt()));
        createdAt.getStyleClass().add("task-date");

        Button deleteButton = new Button("Удалить");
        deleteButton.getStyleClass().add("delete-button");
        deleteButton.setOnAction(event -> confirmDelete(task));
        HBox footer = new HBox(statusPicker, spacer(), deleteButton);
        footer.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(top, title, description, createdAt, footer);
        return card;
    }

    private void updatePagination(PagedTasks response) {
        previousButton.setDisable(response == null || response.page() == 0);
        nextButton.setDisable(response == null || !response.hasNext());
        pageLabel.setText(response == null || response.totalPages() == 0
                ? "Страница —" : "Страница " + (response.page() + 1) + " из " + response.totalPages());
    }

    private void openTaskDialog(Task task) {
        Dialog<TaskDraft> dialog = new Dialog<>();
        dialog.setTitle(task == null ? "Новая задача" : "Изменить задачу #" + task.id());
        dialog.setHeaderText(task == null ? "Добавьте задачу в проект" : "Измените данные и статус задачи");
        dialog.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE),
                new ButtonType(task == null ? "Создать задачу" : "Сохранить", ButtonBar.ButtonData.OK_DONE));

        TextField titleField = new TextField(task == null ? "" : task.title());
        titleField.setPromptText("Например, подготовить отчёт");
        titleField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= 100 ? change : null));
        TextArea descriptionArea = new TextArea(task == null || task.description() == null ? "" : task.description());
        descriptionArea.setPromptText("Что нужно сделать?");
        descriptionArea.setWrapText(true);
        descriptionArea.setPrefRowCount(5);
        descriptionArea.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= 2_000 ? change : null));
        ComboBox<TaskStatus> statusPicker = new ComboBox<>(FXCollections.observableArrayList(TaskStatus.values()));
        statusPicker.setValue(task == null ? TaskStatus.NEW : task.status());
        statusPicker.setMaxWidth(Double.MAX_VALUE);

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(10);
        fields.setPadding(new Insets(4, 0, 4, 0));
        fields.getColumnConstraints().add(new ColumnConstraints(105));
        ColumnConstraints inputColumn = new ColumnConstraints();
        inputColumn.setHgrow(Priority.ALWAYS);
        fields.getColumnConstraints().add(inputColumn);
        fields.add(new Label("Название"), 0, 0);
        fields.add(titleField, 1, 0);
        fields.add(new Label("Описание"), 0, 1);
        fields.add(descriptionArea, 1, 1);
        fields.add(new Label("Статус"), 0, 2);
        fields.add(statusPicker, 1, 2);
        dialog.getDialogPane().setContent(fields);
        dialog.getDialogPane().setPrefWidth(520);
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(
                dialog.getDialogPane().getButtonTypes().get(1));
        BooleanProperty saving = new SimpleBooleanProperty(false);
        saveButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> saving.get() || titleField.getText().trim().isEmpty(),
                titleField.textProperty(), saving));
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            TaskDraft draft = new TaskDraft(
                    titleField.getText().trim(), descriptionArea.getText().trim(), statusPicker.getValue());
            saveTask(task, draft, dialog, saveButton, saving);
        });
        dialog.show();
    }

    private void saveTask(
            Task task, TaskDraft draft, Dialog<?> dialog, Button saveButton, BooleanProperty saving
    ) {
        saving.set(true);
        saveButton.setText("Сохраняем…");
        TaskInput input = new TaskInput(draft.title(), draft.description(), draft.status());
        CompletableFuture<Task> request = task == null
                ? api.createTask(input) : api.updateTask(task.id(), input);
        request.whenComplete((saved, error) -> Platform.runLater(() -> {
            if (error != null) {
                showErrorAlert("Не удалось сохранить задачу", errorMessage(error));
                saveButton.setText(task == null ? "Создать задачу" : "Сохранить");
                saving.set(false);
                return;
            }
            dialog.close();
            showFeedback(task == null ? "Задача создана." : "Изменения сохранены.", true);
            loadTasks();
        }));
    }

    private void updateTaskStatus(Task task, TaskStatus status) {
        api.updateTask(task.id(), new TaskInput(task.title(), task.description(), status))
                .whenComplete((updated, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        showFeedback("Не удалось изменить статус задачи: " + errorMessage(error));
                        loadTasks();
                        return;
                    }
                    showFeedback("Статус задачи #" + task.id() + " изменён на «" + status.label() + "».", true);
                    loadTasks();
                }));
    }

    private void confirmDelete(Task task) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Удаление задачи");
        confirmation.setHeaderText("Удалить задачу #" + task.id() + "?");
        confirmation.setContentText(task.title() + "\nЭто действие нельзя отменить.");
        confirmation.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button ->
                api.deleteTask(task.id()).whenComplete((ignored, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        showErrorAlert("Не удалось удалить задачу", errorMessage(error));
                        return;
                    }
                    showFeedback("Задача удалена.", true);
                    loadTasks();
                })));
    }

    private void setConnection(String message, boolean connected) {
        connectionLabel.setText("●  " + message);
        connectionLabel.getStyleClass().removeAll("connection-online", "connection-offline");
        connectionLabel.getStyleClass().add(connected ? "connection-online" : "connection-offline");
    }

    private void showFeedback(String message) {
        showFeedback(message, false);
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("feedback-error", "feedback-success");
        feedbackLabel.getStyleClass().add(success ? "feedback-success" : "feedback-error");
        feedbackLabel.setManaged(true);
        feedbackLabel.setVisible(true);
    }

    private void hideFeedback() {
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String errorMessage(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause.getCause() != null) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "Неизвестная ошибка" : cause.getMessage();
    }

}

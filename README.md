# Task Flow

JavaFX-клиент для REST API трекера задач из проекта `Rest Api`. Клиент подключается к `http://localhost:8080`; поддерживает список и поиск задач, фильтрацию по статусу, создание, редактирование, смену статуса и удаление.

## Запуск

Нужны JDK 21+ и Maven.

1. Запустите REST API в отдельном терминале из каталога проекта `Rest Api`:

   ```bash
   mvn spring-boot:run
   ```

2. Из корня этого проекта запустите клиент:

   ```bash
   mvn -pl api-manager-client javafx:run
   ```

Для сборки исполняемого JAR со всеми библиотеками:

```bash
mvn -pl api-manager-client clean package
```

Создаётся `api-manager-client/target/task-flow.jar`. Запустите графическое приложение командой:

```bash
java -jar api-manager-client/target/task-flow.jar
```

Точку входа можно также запустить из IntelliJ IDEA: импортируйте корневой `pom.xml` как Maven-проект и выполните `main()` в `api-manager-client/src/main/java/com/example/apiclient/App.java`.

## Терминальный режим

```bash
java -jar api-manager-client/target/task-flow.jar --cli
```

В терминальном меню доступны просмотр задач с поиском и фильтром, создание, редактирование, смена статуса и удаление. Оба интерфейса используют общий REST Assured-клиент.

Адрес API по умолчанию — `http://localhost:8080`. Чтобы изменить его:

```bash
TASKTRACKER_API_BASE_URL=http://localhost:8081 mvn -pl api-manager-client javafx:run
```

Для терминального режима используйте тот же API-адрес:

```bash
TASKTRACKER_API_BASE_URL=http://localhost:8081 java -jar api-manager-client/target/task-flow.jar --cli
```

Клиент использует `/api/v1/tasks` и поля `title`, `description`, `status`, `createdAt` из контракта API. Название ограничено 100 символами, описание — 2 000.
JAR включает JavaFX-нужные библиотеки и собирается для текущей ОС и архитектуры.

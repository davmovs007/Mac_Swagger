package com.example.apiclient;

import javafx.application.Application;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--cli".equalsIgnoreCase(args[0])) {
            ConsoleTaskApp.run();
            return;
        }
        Application.launch(TaskTrackerApplication.class, args);
    }
}

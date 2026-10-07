package com.example.apiclient;

public enum TaskStatus {
    NEW("Новая"),
    IN_PROGRESS("В работе"),
    DONE("Завершена");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}

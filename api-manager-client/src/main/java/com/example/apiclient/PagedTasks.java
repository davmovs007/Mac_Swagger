package com.example.apiclient;

import java.util.List;

public record PagedTasks(
        List<Task> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}

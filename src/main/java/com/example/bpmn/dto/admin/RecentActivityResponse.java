package com.example.bpmn.dto.admin;

import java.time.LocalDateTime;

public record RecentActivityResponse(
        String message,
        LocalDateTime date
) {
}

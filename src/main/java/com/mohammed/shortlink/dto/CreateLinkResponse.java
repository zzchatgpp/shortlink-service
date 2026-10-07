package com.mohammed.shortlink.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateLinkResponse(
        @Schema(description = "Unique auto-increment database ID", example = "1")
        Long id,
        @Schema(description = "Case-sensitive Base62 encoding of the ID", example = "1")
        String shortCode,
        @Schema(description = "Generated redirect URL", example = "http://localhost:8080/s/1", format = "uri")
        String shortUrl,
        @Schema(description = "Original destination URL", example = "https://example.com/articles/java", format = "uri")
        String originalUrl,
        @Schema(description = "UTC creation timestamp", example = "2026-10-07T10:30:00Z", format = "date-time")
        Instant createdAt,
        @Schema(description = "Optional expiry timestamp; null means no expiry", example = "2030-01-01T00:00:00Z", format = "date-time", nullable = true)
        Instant expiresAt
) {
}

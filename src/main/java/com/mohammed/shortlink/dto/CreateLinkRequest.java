package com.mohammed.shortlink.dto;

import com.mohammed.shortlink.validation.HttpUrl;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

public record CreateLinkRequest(
        @Schema(description = "Absolute HTTP/HTTPS destination without embedded credentials; percent-encode spaces",
                example = "https://example.com/articles/java", format = "uri", maxLength = 2048, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "originalUrl is required")
        @Size(max = 2048, message = "originalUrl must contain at most 2048 characters")
        @HttpUrl String originalUrl,
        @Schema(description = "Optional future expiry up to 9999-12-31T23:59:59Z, in ISO-8601 format with a timezone; omit or use null for no expiry",
                type = "string", format = "date-time", example = "2030-01-01T00:00:00Z", nullable = true)
        @Future(message = "expiresAt must be in the future") Instant expiresAt
) {
}

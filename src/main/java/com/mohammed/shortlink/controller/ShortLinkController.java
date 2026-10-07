package com.mohammed.shortlink.controller;

import com.mohammed.shortlink.dto.CreateLinkRequest;
import com.mohammed.shortlink.dto.CreateLinkResponse;
import com.mohammed.shortlink.dto.LinkAnalyticsResponse;
import com.mohammed.shortlink.service.ShortLinkService;
import jakarta.validation.Valid;
import com.mohammed.shortlink.config.ApiExamples;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@Tag(name = "Links", description = "Create short links and inspect their statistics")
@RequestMapping("/api/links")
@Profile("!bootstrap")
public class ShortLinkController {
    private final ShortLinkService service;

    public ShortLinkController(ShortLinkService service) {
        this.service = service;
    }

    @Operation(operationId = "createShortLink", summary = "Create a short link",
            description = "Accepts an absolute HTTP/HTTPS URL of up to 2048 characters, without embedded credentials. "
                    + "Optional expiresAt must be in the future. Omit it or use null for no expiry. "
                    + "Repeated destination URLs receive different codes. ID, code and timestamps in responses vary.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateLinkRequest.class),
                            examples = {
                                    @ExampleObject(name = "Permanent link", value = ApiExamples.PERMANENT_REQUEST),
                                    @ExampleObject(name = "Expiring link", value = ApiExamples.EXPIRING_REQUEST)
                            })))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Link created",
                    headers = @Header(name = "Location", description = "Generated short URL",
                            schema = @Schema(type = "string", format = "uri")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = CreateLinkResponse.class),
                            examples = @ExampleObject(value = ApiExamples.CREATED))),
            @ApiResponse(responseCode = "400", description = "Invalid URL, expiry or JSON body",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                            examples = {
                                    @ExampleObject(name = "Validation error", value = ApiExamples.VALIDATION_ERROR),
                                    @ExampleObject(name = "Malformed body", value = ApiExamples.MALFORMED_BODY)
                            })),
            @ApiResponse(responseCode = "415", description = "Request Content-Type must be application/json",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    public ResponseEntity<CreateLinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
        CreateLinkResponse response = service.create(request);
        return ResponseEntity.created(URI.create(response.shortUrl())).body(response);
    }

    @Operation(operationId = "getLinkAnalytics", summary = "Get link analytics",
            description = "Reads current stored statistics from MySQL. Does not count as a click. "
                    + "Expired links remain available with expired=true. A new link has clickCount=0 and lastClickedAt=null.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current statistics, including for expired links",
                    headers = @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LinkAnalyticsResponse.class),
                            examples = @ExampleObject(value = ApiExamples.ANALYTICS))),
            @ApiResponse(responseCode = "404", description = "Unknown, malformed or overflowing short code",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = ApiExamples.NOT_FOUND)))
    })
    @GetMapping("/{code}/stats")
    public ResponseEntity<LinkAnalyticsResponse> analytics(
            @Parameter(description = "Case-sensitive shortCode returned by creation", example = "1", required = true)
            @PathVariable String code) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getAnalytics(code));
    }
}

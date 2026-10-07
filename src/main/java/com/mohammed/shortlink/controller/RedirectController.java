package com.mohammed.shortlink.controller;

import com.mohammed.shortlink.service.ShortLinkService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@Tag(name = "Redirects", description = "Open short links; active GET requests count as visits")
@Profile("!bootstrap")
public class RedirectController {
    private final ShortLinkService service;

    public RedirectController(ShortLinkService service) {
        this.service = service;
    }

    @Operation(operationId = "redirectShortLink", summary = "Redirect to the destination",
            description = "Returns HTTP 302 with a Location header and no body. Counts one active GET redirect. "
                    + "HEAD is also supported and does not count. Open shortUrl in a browser or use curl -i to inspect headers. "
                    + "Swagger's browser request may follow the redirect to the external destination.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Active link; redirect to the original URL",
                    headers = {
                            @Header(name = "Location", description = "Original destination", schema = @Schema(type = "string", format = "uri")),
                            @Header(name = "Cache-Control", schema = @Schema(type = "string", example = "no-store"))
                    }, content = @Content),
            @ApiResponse(responseCode = "404", description = "Unknown, malformed or overflowing code",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = ApiExamples.NOT_FOUND))),
            @ApiResponse(responseCode = "410", description = "Link expiry is at or before the current time",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class),
                            examples = @ExampleObject(value = ApiExamples.EXPIRED)))
    })
    @GetMapping("/s/{code}")
    public ResponseEntity<Void> redirect(
            @Parameter(description = "Case-sensitive shortCode returned by creation", example = "1", required = true)
            @PathVariable String code, HttpServletRequest request) {
        String destination = "HEAD".equals(request.getMethod())
                ? service.resolveDestination(code)
                : service.redirectAndRecordClick(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(destination))
                // Each visit must reach the service for click tracking and expiry checks.
                .cacheControl(CacheControl.noStore())
                .build();
    }
}

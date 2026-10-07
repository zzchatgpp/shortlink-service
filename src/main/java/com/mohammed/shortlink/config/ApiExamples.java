package com.mohammed.shortlink.config;

/** Compile-time constants shared by OpenAPI annotations. */
public final class ApiExamples {
    private ApiExamples() { }

    public static final String PERMANENT_REQUEST = """
            {"originalUrl":"https://example.com/articles/java"}
            """;
    public static final String EXPIRING_REQUEST = """
            {"originalUrl":"https://example.com/event","expiresAt":"2030-01-01T00:00:00Z"}
            """;
    public static final String CREATED = """
            {"id":1,"shortCode":"1","shortUrl":"http://localhost:8080/s/1",
             "originalUrl":"https://example.com/articles/java",
             "createdAt":"2026-10-07T10:30:00Z","expiresAt":null}
            """;
    public static final String VALIDATION_ERROR = """
            {"type":"about:blank","title":"Invalid request","status":400,
             "detail":"Request validation failed","instance":"/api/links",
             "errors":{"originalUrl":"originalUrl is required"}}
            """;
    public static final String MALFORMED_BODY = """
            {"type":"about:blank","title":"Invalid request body","status":400,
             "detail":"Provide valid JSON. expiresAt must be an ISO-8601 timestamp with a timezone, or null.",
             "instance":"/api/links"}
            """;
    public static final String ANALYTICS = """
            {"id":1,"shortCode":"1","shortUrl":"http://localhost:8080/s/1",
             "originalUrl":"https://example.com/articles/java",
             "createdAt":"2026-10-07T10:30:00Z","expiresAt":null,
             "clickCount":3,"lastClickedAt":"2026-10-07T10:35:00Z","expired":false}
            """;
    public static final String NOT_FOUND = """
            {"type":"about:blank","title":"Link not found","status":404,
             "detail":"No link exists for this short code"}
            """;
    public static final String EXPIRED = """
            {"type":"about:blank","title":"Link expired","status":410,
             "detail":"This short link has expired"}
            """;
}

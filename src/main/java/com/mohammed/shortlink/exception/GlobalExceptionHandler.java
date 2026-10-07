package com.mohammed.shortlink.exception;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Provide valid JSON. expiresAt must be an ISO-8601 timestamp with a timezone, or null.");
        problem.setTitle("Invalid request body");
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(InvalidExpiryException.class)
    public ProblemDetail handleInvalidExpiry(InvalidExpiryException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid expiry");
        problem.setProperty("errors", Map.of("expiresAt", exception.getMessage()));
        return problem;
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleLinkNotFound(LinkNotFoundException exception) {
        return linkError(HttpStatus.NOT_FOUND, "Link not found", exception.getMessage());
    }

    @ExceptionHandler(LinkExpiredException.class)
    public ResponseEntity<ProblemDetail> handleLinkExpired(LinkExpiredException exception) {
        return linkError(HttpStatus.GONE, "Link expired", exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> linkError(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(problem);
    }
}

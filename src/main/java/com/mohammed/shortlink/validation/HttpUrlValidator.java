package com.mohammed.shortlink.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // @NotBlank handles missing input separately.
        return value == null || value.isBlank() || isHttpUrl(value);
    }

    public static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = new URI(value).parseServerAuthority();
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535));
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}

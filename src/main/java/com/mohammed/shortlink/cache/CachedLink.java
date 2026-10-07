package com.mohammed.shortlink.cache;

import java.time.Instant;

/** Only routing data is cached; click counts remain authoritative in MySQL. */
public record CachedLink(long id, String originalUrl, Instant expiresAt) {
}

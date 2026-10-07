package com.mohammed.shortlink.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohammed.shortlink.validation.HttpUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Component
@Profile("!bootstrap")
public class RedisLinkCache {
    private static final Logger log = LoggerFactory.getLogger(RedisLinkCache.class);
    private static final String KEY_PREFIX = "shortlink:v1:link:";
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final boolean enabled;
    private final long maxTtlMillis;

    public RedisLinkCache(StringRedisTemplate redis, ObjectMapper mapper, Clock clock,
                          @Value("${app.cache.enabled:true}") boolean enabled,
                          @Value("${app.cache.ttl-seconds:3600}") long maxTtlSeconds) {
        if (maxTtlSeconds <= 0) throw new IllegalArgumentException("CACHE_TTL_SECONDS must be positive");
        this.redis = redis;
        this.mapper = mapper;
        this.clock = clock;
        this.enabled = enabled;
        this.maxTtlMillis = Math.multiplyExact(maxTtlSeconds, 1000L);
    }

    public Optional<CachedLink> get(long id) {
        if (!enabled) return Optional.empty();
        try {
            String json = redis.opsForValue().get(key(id));
            if (json == null) return Optional.empty();
            CachedLink link = mapper.readValue(json, CachedLink.class);
            if (link == null || link.id() != id || link.originalUrl() == null
                    || link.originalUrl().length() > 2048
                    || !HttpUrlValidator.isHttpUrl(link.originalUrl())
                    || (link.expiresAt() != null && !link.expiresAt().isAfter(clock.instant()))) {
                evict(id);
                return Optional.empty();
            }
            return Optional.of(link);
        } catch (JsonProcessingException exception) {
            log.warn("Discarding malformed link cache entry");
            evict(id);
            return Optional.empty();
        } catch (DataAccessException exception) {
            log.warn("Redis cache read failed; using database");
            return Optional.empty();
        }
    }

    public void put(CachedLink link) {
        if (!enabled) return;
        long ttlMillis = maxTtlMillis;
        if (link.expiresAt() != null) {
            ttlMillis = Math.min(ttlMillis, Duration.between(clock.instant(), link.expiresAt()).toMillis());
        }
        // Redis TTL has millisecond precision. Do not round a short expiry up.
        if (ttlMillis <= 0) return;
        try {
            redis.opsForValue().set(key(link.id()), mapper.writeValueAsString(link), Duration.ofMillis(ttlMillis));
        } catch (JsonProcessingException | DataAccessException exception) {
            log.warn("Redis cache write failed; continuing with database");
        }
    }

    public void evict(long id) {
        if (!enabled) return;
        try {
            redis.delete(key(id));
        } catch (DataAccessException exception) {
            log.warn("Redis cache eviction failed; expiry checks remain active");
        }
    }

    private String key(long id) {
        return KEY_PREFIX + id;
    }
}

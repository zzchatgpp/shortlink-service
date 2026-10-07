package com.mohammed.shortlink.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisLinkCacheTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ObjectMapper mapper;
    private RedisLinkCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        mapper = new ObjectMapper().findAndRegisterModules();
        cache = new RedisLinkCache(redis, mapper, Clock.fixed(NOW, ZoneOffset.UTC), true, 3600);
    }

    @Test
    void cachesPermanentLinksForTheConfiguredMaximum() throws Exception {
        CachedLink link = new CachedLink(1L, "https://example.com", null);
        cache.put(link);
        verify(values).set("shortlink:v1:link:1", mapper.writeValueAsString(link), Duration.ofHours(1));
    }

    @Test
    void ttlDoesNotExceedRemainingLinkLifetime() throws Exception {
        CachedLink link = new CachedLink(1L, "https://example.com", NOW.plusMillis(1500));
        cache.put(link);
        verify(values).set("shortlink:v1:link:1", mapper.writeValueAsString(link), Duration.ofMillis(1500));
    }

    @Test
    void distantExpiryStillUsesTheMaximumTtl() throws Exception {
        CachedLink link = new CachedLink(1L, "https://example.com", NOW.plusSeconds(7200));
        cache.put(link);
        verify(values).set("shortlink:v1:link:1", mapper.writeValueAsString(link), Duration.ofHours(1));
    }

    @Test
    void doesNotCacheExpiredOrSubMillisecondLinks() {
        cache.put(new CachedLink(1L, "https://example.com", NOW));
        cache.put(new CachedLink(2L, "https://example.com", NOW.minusSeconds(1)));
        cache.put(new CachedLink(3L, "https://example.com", NOW.plusNanos(500000)));
        verifyNoInteractions(values);
    }

    @Test
    void readsSerializedRoutingData() throws Exception {
        CachedLink link = new CachedLink(1L, "https://example.com/a?q=b", NOW.plusSeconds(60));
        when(values.get("shortlink:v1:link:1")).thenReturn(mapper.writeValueAsString(link));
        assertThat(cache.get(1L)).contains(link);
    }

    @Test
    void missingEntryIsACacheMiss() {
        assertThat(cache.get(1L)).isEmpty();
    }

    @Test
    void discardsExpiredEntryEvenWhenRedisStillHasIt() throws Exception {
        when(values.get("shortlink:v1:link:1")).thenReturn(mapper.writeValueAsString(
                new CachedLink(1L, "https://example.com", NOW)));
        assertThat(cache.get(1L)).isEmpty();
        verify(redis).delete("shortlink:v1:link:1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "null", "{}",
            "{\"id\":2,\"originalUrl\":\"https://example.com\"}",
            "{\"id\":1,\"originalUrl\":\"javascript:alert(1)\"}"})
    void discardsCorruptOrMismatchedEntries(String value) {
        when(values.get("shortlink:v1:link:1")).thenReturn(value);
        assertThat(cache.get(1L)).isEmpty();
        verify(redis).delete("shortlink:v1:link:1");
    }

    @Test
    void readFailureFallsBackToDatabase() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("unavailable"));
        assertThat(cache.get(1L)).isEmpty();
    }

    @Test
    void writeFailureDoesNotPreventRedirects() {
        doThrow(new RedisConnectionFailureException("unavailable"))
                .when(values).set(anyString(), anyString(), any(Duration.class));
        assertThatCode(() -> cache.put(new CachedLink(1L, "https://example.com", null))).doesNotThrowAnyException();
    }

    @Test
    void evictionFailureDoesNotEscape() {
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("unavailable"));
        assertThatCode(() -> cache.evict(1L)).doesNotThrowAnyException();
    }

    @Test
    void disablingCachingDoesNotCallRedis() {
        RedisLinkCache disabled = new RedisLinkCache(redis, mapper, Clock.fixed(NOW, ZoneOffset.UTC), false, 3600);
        assertThat(disabled.get(1L)).isEmpty();
        disabled.put(new CachedLink(1L, "https://example.com", null));
        disabled.evict(1L);
        verifyNoInteractions(values);
        verify(redis, never()).delete(anyString());
    }
}

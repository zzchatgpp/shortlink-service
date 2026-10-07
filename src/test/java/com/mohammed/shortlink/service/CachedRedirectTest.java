package com.mohammed.shortlink.service;

import com.mohammed.shortlink.cache.CachedLink;
import com.mohammed.shortlink.cache.RedisLinkCache;
import com.mohammed.shortlink.entity.ShortLink;
import com.mohammed.shortlink.exception.LinkExpiredException;
import com.mohammed.shortlink.exception.LinkNotFoundException;
import com.mohammed.shortlink.repository.ShortLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachedRedirectTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private ShortLinkRepository repository;
    private RedisLinkCache cache;
    private ShortLinkService service;

    @BeforeEach
    void setup() {
        repository = mock(ShortLinkRepository.class);
        cache = mock(RedisLinkCache.class);
        service = new ShortLinkService(repository, cache, Clock.fixed(NOW, ZoneOffset.UTC), "https://short.example");
    }

    @Test
    void cacheHitSkipsLinkLookupAndStillWritesTheClick() {
        when(cache.get(1L)).thenReturn(Optional.of(new CachedLink(1L, "https://example.com", null)));
        when(repository.recordClickIfActive(1L, NOW)).thenReturn(1);
        assertThat(service.redirectAndRecordClick("1")).isEqualTo("https://example.com");
        verify(repository, never()).findById(anyLong());
        verify(repository).recordClickIfActive(1L, NOW);
        verify(cache, never()).put(any());
    }

    @Test
    void cacheMissLoadsAndCachesCommittedRoutingData() {
        ShortLink stored = new ShortLink("https://example.com", null);
        ReflectionTestUtils.setField(stored, "id", 1L);
        when(cache.get(1L)).thenReturn(Optional.empty());
        when(repository.findById(1L)).thenReturn(Optional.of(stored));
        when(repository.recordClickIfActive(1L, NOW)).thenReturn(1);
        assertThat(service.redirectAndRecordClick("1")).isEqualTo("https://example.com");
        verify(cache).put(new CachedLink(1L, "https://example.com", null));
    }

    @Test
    void expiredCachedLinkNeverRedirectsOrCountsAClick() {
        when(cache.get(1L)).thenReturn(Optional.of(new CachedLink(1L, "https://example.com", NOW)));
        assertThatThrownBy(() -> service.redirectAndRecordClick("1")).isInstanceOf(LinkExpiredException.class);
        verify(cache).evict(1L);
        verifyNoInteractions(repository);
    }

    @Test
    void deletedCachedLinkIsEvictedWhenTheAtomicUpdateFindsNoRow() {
        when(cache.get(1L)).thenReturn(Optional.of(new CachedLink(1L, "https://example.com", null)));
        when(repository.recordClickIfActive(1L, NOW)).thenReturn(0);
        when(repository.existsById(1L)).thenReturn(false);
        assertThatThrownBy(() -> service.redirectAndRecordClick("1")).isInstanceOf(LinkNotFoundException.class);
        verify(cache).evict(1L);
    }

    @Test
    void headCacheHitDoesNotWriteToDatabase() {
        when(cache.get(1L)).thenReturn(Optional.of(new CachedLink(1L, "https://example.com", null)));
        assertThat(service.resolveDestination("1")).isEqualTo("https://example.com");
        verifyNoInteractions(repository);
    }
}

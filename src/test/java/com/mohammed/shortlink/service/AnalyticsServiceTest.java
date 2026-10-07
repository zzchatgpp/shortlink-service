package com.mohammed.shortlink.service;

import com.mohammed.shortlink.cache.RedisLinkCache;
import com.mohammed.shortlink.dto.LinkAnalyticsResponse;
import com.mohammed.shortlink.entity.ShortLink;
import com.mohammed.shortlink.repository.ShortLinkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AnalyticsServiceTest {
    @Test
    void analyticsUsesDatabaseCountersWithoutTouchingRoutingCache() {
        ShortLinkRepository repository = mock(ShortLinkRepository.class);
        RedisLinkCache cache = mock(RedisLinkCache.class);
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        ShortLink link = new ShortLink("https://example.com", null);
        ReflectionTestUtils.setField(link, "id", 62L);
        ReflectionTestUtils.setField(link, "clickCount", 42L);
        ReflectionTestUtils.setField(link, "lastClickedAt", now.minusSeconds(1));
        when(repository.findById(62L)).thenReturn(Optional.of(link));
        ShortLinkService service = new ShortLinkService(repository, cache,
                Clock.fixed(now, ZoneOffset.UTC), "https://short.example");
        LinkAnalyticsResponse stats = service.getAnalytics("10");
        assertThat(stats.clickCount()).isEqualTo(42);
        assertThat(stats.lastClickedAt()).isEqualTo(now.minusSeconds(1));
        assertThat(stats.shortCode()).isEqualTo("10");
        assertThat(stats.expired()).isFalse();
        verify(repository).findById(62L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(cache);
    }
}

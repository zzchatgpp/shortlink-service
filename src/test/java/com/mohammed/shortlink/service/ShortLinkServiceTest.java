package com.mohammed.shortlink.service;

import com.mohammed.shortlink.dto.CreateLinkRequest;
import com.mohammed.shortlink.exception.InvalidExpiryException;
import com.mohammed.shortlink.repository.ShortLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ShortLinkServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {"2026-10-07T10:00:00Z", "2026-10-07T09:59:59Z"})
    void rejectsExpiryAtOrBeforePersistenceTimeWithoutSaving(String expiry) {
        ShortLinkRepository repository = mock(ShortLinkRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneOffset.UTC);
        ShortLinkService service = new ShortLinkService(repository, mock(com.mohammed.shortlink.cache.RedisLinkCache.class), clock, "https://short.example");
        assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", Instant.parse(expiry))))
                .isInstanceOf(InvalidExpiryException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void expiryPassingBetweenLookupAndUpdateDoesNotRedirect() {
        ShortLinkRepository repository = mock(ShortLinkRepository.class);
        Clock clock = mock(Clock.class);
        Instant beforeExpiry = Instant.parse("2030-01-01T00:00:00Z");
        Instant atExpiry = beforeExpiry.plusSeconds(1);
        com.mohammed.shortlink.entity.ShortLink link =
                new com.mohammed.shortlink.entity.ShortLink("https://example.com", atExpiry);
        org.springframework.test.util.ReflectionTestUtils.setField(link, "id", 1L);
        when(clock.instant()).thenReturn(beforeExpiry, beforeExpiry, atExpiry);
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(link));
        when(repository.recordClickIfActive(1L, atExpiry)).thenReturn(0);
        when(repository.existsById(1L)).thenReturn(true);
        ShortLinkService service = new ShortLinkService(repository, mock(com.mohammed.shortlink.cache.RedisLinkCache.class), clock, "https://short.example");
        assertThatThrownBy(() -> service.redirectAndRecordClick("1"))
                .isInstanceOf(com.mohammed.shortlink.exception.LinkExpiredException.class);
    }
}

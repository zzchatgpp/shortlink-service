package com.mohammed.shortlink.service;

import com.mohammed.shortlink.cache.RedisLinkCache;
import com.mohammed.shortlink.dto.CreateLinkRequest;
import com.mohammed.shortlink.dto.CreateLinkResponse;
import com.mohammed.shortlink.entity.ShortLink;
import com.mohammed.shortlink.exception.InvalidExpiryException;
import com.mohammed.shortlink.exception.LinkExpiredException;
import com.mohammed.shortlink.exception.LinkNotFoundException;
import com.mohammed.shortlink.repository.ShortLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreationServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private ShortLinkRepository repository;
    private RedisLinkCache cache;
    private ShortLinkService service;

    @BeforeEach
    void setup() {
        repository = mock(ShortLinkRepository.class);
        cache = mock(RedisLinkCache.class);
        service = new ShortLinkService(repository, cache, Clock.fixed(NOW, ZoneOffset.UTC), "https://short.example/");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"2039-01-01T00:00:00Z"})
    void createsCodeFromDatabaseAssignedIdAndPreservesOptionalExpiry(String expiryString) {
        Instant expiry = expiryString == null ? null : Instant.parse(expiryString);
        when(repository.saveAndFlush(any(ShortLink.class))).thenAnswer(invocation -> {
            ShortLink saved = invocation.getArgument(0);
            assertThat(saved.getOriginalUrl()).isEqualTo("https://example.com/article?q=java#intro");
            assertThat(saved.getExpiresAt()).isEqualTo(expiry);
            ReflectionTestUtils.setField(saved, "id", 125L);
            ReflectionTestUtils.setField(saved, "createdAt", NOW);
            return saved;
        });
        CreateLinkResponse response = service.create(new CreateLinkRequest("https://example.com/article?q=java#intro", expiry));
        assertThat(response.id()).isEqualTo(125L);
        assertThat(response.shortCode()).isEqualTo("21");
        assertThat(response.shortUrl()).isEqualTo("https://short.example/s/21");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.expiresAt()).isEqualTo(expiry);
        verify(repository).saveAndFlush(any(ShortLink.class));
        // A new link is not published into Redis before its transaction commits.
        verifyNoInteractions(cache);
    }

    @Test
    void databaseFailureDoesNotReturnAFabricatedCodeOrPopulateCache() {
        when(repository.saveAndFlush(any(ShortLink.class))).thenThrow(new DataIntegrityViolationException("save failed"));
        assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(cache);
    }

    @Test
    void rejectsExpiryBeyondMysqlRangeBeforeSaving() {
        assertThatThrownBy(() -> service.create(new CreateLinkRequest("https://example.com", Instant.parse("+10000-01-01T00:00:00Z"))))
                .isInstanceOf(InvalidExpiryException.class).hasMessageContaining("9999-12-31");
        verifyNoInteractions(repository, cache);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "01", "abc-", "aZl8N0y58M8"})
    void malformedCodeIsRejectedBeforeAnyCacheOrDatabaseCall(String code) {
        assertThatThrownBy(() -> service.redirectAndRecordClick(code)).isInstanceOf(LinkNotFoundException.class);
        verifyNoInteractions(repository, cache);
    }

    @Test
    void unknownCodeIsNotCachedOrCounted() {
        when(cache.get(1L)).thenReturn(Optional.empty());
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.redirectAndRecordClick("1")).isInstanceOf(LinkNotFoundException.class);
        verify(repository, never()).recordClickIfActive(anyLong(), any());
        verify(cache, never()).put(any());
    }

    @Test
    void expiredDatabaseLinkIsNotCachedOrCounted() {
        ShortLink expired = new ShortLink("https://example.com", NOW);
        ReflectionTestUtils.setField(expired, "id", 1L);
        when(cache.get(1L)).thenReturn(Optional.empty());
        when(repository.findById(1L)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.redirectAndRecordClick("1")).isInstanceOf(LinkExpiredException.class);
        verify(repository, never()).recordClickIfActive(anyLong(), any());
        verify(cache, never()).put(any());
    }
}

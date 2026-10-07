package com.mohammed.shortlink.repository;

import com.mohammed.shortlink.entity.ShortLink;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ShortLinkRepositoryTest {
    @Autowired
    private ShortLinkRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndLoadsLinkWithExpiryAndInitialAnalytics() {
        Instant expiry = Instant.parse("2030-01-01T12:00:00Z");
        ShortLink saved = repository.saveAndFlush(new ShortLink("https://example.com/a", expiry));
        Long id = saved.getId();
        entityManager.clear();

        ShortLink loaded = repository.findById(id).orElseThrow();
        assertThat(id).isPositive();
        assertThat(loaded.getOriginalUrl()).isEqualTo("https://example.com/a");
        assertThat(loaded.getExpiresAt()).isEqualTo(expiry);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getClickCount()).isZero();
        assertThat(loaded.getLastClickedAt()).isNull();
    }

    @Test
    void assignsDistinctIdsEvenForTheSameOriginalUrl() {
        ShortLink first = repository.saveAndFlush(new ShortLink("https://example.com", null));
        ShortLink second = repository.saveAndFlush(new ShortLink("https://example.com", null));
        assertThat(first.getId()).isNotEqualTo(second.getId());
        entityManager.clear();
        assertThat(repository.findById(first.getId()).orElseThrow().getExpiresAt()).isNull();
    }

    @Test
    void returnsEmptyForUnknownId() {
        assertThat(repository.findById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    void olderConcurrentTimestampDoesNotMoveLastClickBackwards() {
        ShortLink link = repository.saveAndFlush(new ShortLink("https://example.com", null));
        Instant newer = Instant.parse("2030-01-01T12:00:02Z");
        Instant older = newer.minusSeconds(1);
        assertThat(repository.recordClickIfActive(link.getId(), newer)).isEqualTo(1);
        assertThat(repository.recordClickIfActive(link.getId(), older)).isEqualTo(1);
        ShortLink clicked = repository.findById(link.getId()).orElseThrow();
        assertThat(clicked.getClickCount()).isEqualTo(2);
        assertThat(clicked.getLastClickedAt()).isEqualTo(newer);
    }

    @Test
    void atomicUpdateRejectsAVisitAtExpiryWithoutChangingAnalytics() {
        Instant expiry = Instant.parse("2030-01-01T12:00:00Z");
        ShortLink link = repository.saveAndFlush(new ShortLink("https://example.com", expiry));
        assertThat(repository.recordClickIfActive(link.getId(), expiry)).isZero();
        ShortLink unchanged = repository.findById(link.getId()).orElseThrow();
        assertThat(unchanged.getClickCount()).isZero();
        assertThat(unchanged.getLastClickedAt()).isNull();
    }

    @Test
    void atomicUpdateRejectsUnknownId() {
        assertThat(repository.recordClickIfActive(Long.MAX_VALUE, Instant.now())).isZero();
    }
}

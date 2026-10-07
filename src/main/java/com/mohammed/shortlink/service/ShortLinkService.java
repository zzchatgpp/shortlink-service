package com.mohammed.shortlink.service;

import com.mohammed.shortlink.cache.CachedLink;
import com.mohammed.shortlink.cache.RedisLinkCache;
import com.mohammed.shortlink.dto.CreateLinkRequest;
import com.mohammed.shortlink.dto.CreateLinkResponse;
import com.mohammed.shortlink.dto.LinkAnalyticsResponse;
import com.mohammed.shortlink.entity.ShortLink;
import com.mohammed.shortlink.exception.InvalidExpiryException;
import com.mohammed.shortlink.exception.LinkExpiredException;
import com.mohammed.shortlink.exception.LinkNotFoundException;
import com.mohammed.shortlink.repository.ShortLinkRepository;
import com.mohammed.shortlink.util.Base62;
import com.mohammed.shortlink.validation.HttpUrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

@Service
@Profile("!bootstrap")
public class ShortLinkService {
    private static final Instant MAX_SUPPORTED_EXPIRY = Instant.parse("9999-12-31T23:59:59Z");
    private final ShortLinkRepository repository;
    private final RedisLinkCache cache;
    private final Clock clock;
    private final String baseUrl;

    public ShortLinkService(ShortLinkRepository repository, RedisLinkCache cache, Clock clock,
                           @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.cache = cache;
        this.clock = clock;
        if (!HttpUrlValidator.isHttpUrl(baseUrl)) {
            throw new IllegalArgumentException("APP_BASE_URL must be an HTTP or HTTPS origin");
        }
        URI origin = URI.create(baseUrl);
        if (origin.getQuery() != null || origin.getFragment() != null
                || !(origin.getPath().isEmpty() || origin.getPath().equals("/"))) {
            throw new IllegalArgumentException("APP_BASE_URL must not contain a path, query or fragment");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Transactional
    public CreateLinkResponse create(CreateLinkRequest request) {
        if (request.expiresAt() != null && request.expiresAt().isAfter(MAX_SUPPORTED_EXPIRY)) {
            throw new InvalidExpiryException("expiresAt must not be later than 9999-12-31T23:59:59Z");
        }
        // Recheck at persistence time in case expiry passed after request validation.
        if (request.expiresAt() != null && !request.expiresAt().isAfter(clock.instant())) {
            throw new InvalidExpiryException();
        }
        ShortLink saved = repository.saveAndFlush(new ShortLink(request.originalUrl(), request.expiresAt()));
        String code = Base62.encode(saved.getId());
        return new CreateLinkResponse(saved.getId(), code, baseUrl + "/s/" + code,
                saved.getOriginalUrl(), saved.getCreatedAt(), saved.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public String resolveDestination(String code) {
        return findActiveLink(code).originalUrl();
    }

    @Transactional
    public String redirectAndRecordClick(String code) {
        CachedLink link = findActiveLink(code);
        Instant clickedAt = clock.instant();
        int updated = repository.recordClickIfActive(link.id(), clickedAt);
        if (updated == 0) {
            // Expiry or deletion may have occurred after lookup (including a cache hit).
            cache.evict(link.id());
            if (!repository.existsById(link.id())) throw new LinkNotFoundException();
            throw new LinkExpiredException();
        }
        return link.originalUrl();
    }

    @Transactional(readOnly = true)
    public LinkAnalyticsResponse getAnalytics(String code) {
        // Analytics always reads MySQL; routing cache entries contain no counters.
        ShortLink link = repository.findById(decodeCode(code)).orElseThrow(LinkNotFoundException::new);
        String canonicalCode = Base62.encode(link.getId());
        boolean expired = link.getExpiresAt() != null && !link.getExpiresAt().isAfter(clock.instant());
        return new LinkAnalyticsResponse(link.getId(), canonicalCode, baseUrl + "/s/" + canonicalCode,
                link.getOriginalUrl(), link.getCreatedAt(), link.getExpiresAt(),
                link.getClickCount(), link.getLastClickedAt(), expired);
    }

    private long decodeCode(String code) {
        try {
            return Base62.decode(code);
        } catch (IllegalArgumentException exception) {
            throw new LinkNotFoundException();
        }
    }

    private CachedLink findActiveLink(String code) {
        long id = decodeCode(code);
        CachedLink link = cache.get(id).orElseGet(() -> {
            ShortLink stored = repository.findById(id).orElseThrow(LinkNotFoundException::new);
            CachedLink loaded = new CachedLink(stored.getId(), stored.getOriginalUrl(), stored.getExpiresAt());
            if (loaded.expiresAt() != null && !loaded.expiresAt().isAfter(clock.instant())) {
                throw new LinkExpiredException();
            }
            cache.put(loaded);
            return loaded;
        });
        if (link.expiresAt() != null && !link.expiresAt().isAfter(clock.instant())) {
            cache.evict(id);
            throw new LinkExpiredException();
        }
        return link;
    }
}

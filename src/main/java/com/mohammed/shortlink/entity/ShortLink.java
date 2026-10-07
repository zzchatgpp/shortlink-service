package com.mohammed.shortlink.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "short_links")
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "click_count", nullable = false)
    private long clickCount = 0;

    @Column(name = "last_clicked_at")
    private Instant lastClickedAt;

    protected ShortLink() {
        // JPA requires a no-argument constructor.
    }

    public ShortLink(String originalUrl, Instant expiresAt) {
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    private void initializeCreatedAt() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public long getClickCount() {
        return clickCount;
    }

    public Instant getLastClickedAt() {
        return lastClickedAt;
    }
}

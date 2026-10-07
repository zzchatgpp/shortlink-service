package com.mohammed.shortlink.repository;

import com.mohammed.shortlink.entity.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ShortLink link
            set link.clickCount = link.clickCount + 1,
                link.lastClickedAt = case
                    when link.lastClickedAt is null or link.lastClickedAt < :clickedAt
                    then :clickedAt else link.lastClickedAt end
            where link.id = :id
              and (link.expiresAt is null or link.expiresAt > :clickedAt)
            """)
    int recordClickIfActive(@Param("id") long id, @Param("clickedAt") Instant clickedAt);
}

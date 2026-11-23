package com.swetonyancelmo.encurt_url.repository;

import com.swetonyancelmo.encurt_url.model.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByCode(String code);

    boolean existsByCode(String code);

    @Query("""
            SELECT s FROM ShortUrl s
            WHERE s.code = :code
              AND s.active = true
              AND (s.expiresAt IS NULL OR s.expiresAt > :now)
            """)
    Optional<ShortUrl> findActiveByCode(String code, OffsetDateTime now);
}

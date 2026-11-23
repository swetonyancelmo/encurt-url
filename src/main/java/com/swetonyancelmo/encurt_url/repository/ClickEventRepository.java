package com.swetonyancelmo.encurt_url.repository;

import com.swetonyancelmo.encurt_url.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortUrlId(Long shortUrlId);

    List<ClickEvent> findByShortUrlIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            Long shortUrlId, OffsetDateTime from, OffsetDateTime to);
}

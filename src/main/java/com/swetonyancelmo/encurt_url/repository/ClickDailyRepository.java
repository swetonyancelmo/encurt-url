package com.swetonyancelmo.encurt_url.repository;

import com.swetonyancelmo.encurt_url.model.ClickDaily;
import com.swetonyancelmo.encurt_url.model.ClickDailyId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ClickDailyRepository extends JpaRepository<ClickDaily, ClickDailyId> {

    List<ClickDaily> findByIdShortUrlIdAndIdDayBetweenOrderByIdDayAsc(
            Long shortUrlId, LocalDate from, LocalDate to);

    @Modifying
    @Query(value = """
            INSERT INTO click_daily (short_url_id, day, count)
            VALUES (:shortUrlId, :day, 1)
            ON CONFLICT (short_url_id, day)
            DO UPDATE SET count = click_daily.count + 1
            """, nativeQuery = true)
    void incrementCount(Long shortUrlId, LocalDate day);
}

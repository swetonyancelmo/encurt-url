package com.swetonyancelmo.encurt_url.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "click_daily")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickDaily {

    @EmbeddedId
    private ClickDailyId id;

    @MapsId("shortUrlId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "short_url_id", nullable = false)
    private ShortUrl shortUrl;

    @Builder.Default
    @Column(nullable = false)
    private long count = 0L;
}

package com.swetonyancelmo.encurt_url.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ClickDailyId implements Serializable {

    @Column(name = "short_url_id", nullable = false)
    private Long shortUrlId;

    @Column(nullable = false)
    private LocalDate day;
}

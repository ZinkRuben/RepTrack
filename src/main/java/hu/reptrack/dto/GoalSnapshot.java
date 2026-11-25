package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Egy edzéscél adatai a haladás számításához. Az {@code enduranceMetric} és a {@code targetDistanceM} csak
 * állóképességi célnál van megadva.
 */
public record GoalSnapshot(Long id, GoalType type, Long exerciseId, BigDecimal targetValue, LocalDate createdOn,
                           EnduranceMetric enduranceMetric, Integer targetDistanceM) {

    /** Testtömeg- és erő-célhoz (mérték és táv nélkül). */
    public GoalSnapshot(Long id, GoalType type, Long exerciseId, BigDecimal targetValue, LocalDate createdOn) {
        this(id, type, exerciseId, targetValue, createdOn, null, null);
    }
}

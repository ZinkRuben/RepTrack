package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Egy edzéscél a profil oldalán. A {@code targetValue} egysége a céltípustól függ (lásd {@link GoalRequest}); az
 * állóképességi célnál a {@code enduranceMetric} adja meg, az „idő adott távon” célnál a táv {@code targetDistanceM}.
 */
public record GoalView(
        Long id,
        GoalType type,
        Long exerciseId,
        String exerciseName,
        BigDecimal targetValue,
        LocalDate deadline,
        LocalDate createdOn,
        EnduranceMetric enduranceMetric,
        Integer targetDistanceM) {
}

package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalView(
        Long id,
        GoalType type,
        Long exerciseId,
        String exerciseName,
        BigDecimal targetValue,
        LocalDate deadline,
        LocalDate createdOn) {
}

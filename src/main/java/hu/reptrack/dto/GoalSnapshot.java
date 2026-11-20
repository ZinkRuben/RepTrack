package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Egy edzéscél adatai a haladás számításához. */
public record GoalSnapshot(Long id, GoalType type, Long exerciseId, BigDecimal targetValue, LocalDate createdOn) {
}

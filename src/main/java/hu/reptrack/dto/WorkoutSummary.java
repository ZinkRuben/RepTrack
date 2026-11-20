package hu.reptrack.dto;

import java.time.Instant;

/** Egy edzés összefoglalója a listanézethez. */
public record WorkoutSummary(
        Long id,
        String title,
        Instant startedAt,
        Instant endedAt,
        Short fatigue,
        Integer exerciseCount) {
}

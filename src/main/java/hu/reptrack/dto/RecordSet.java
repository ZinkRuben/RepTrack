package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Egy lezárt edzés egy szettje a PR-felismeréshez (entitás nélkül). */
public record RecordSet(
        Long setId,
        Long workoutId,
        Instant startedAt,
        ExerciseCategory category,
        CardioType cardioType,
        BigDecimal weightKg,
        Short reps,
        Integer durationSec,
        Integer distanceM) {
}

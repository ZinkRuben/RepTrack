package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Egy szett a hozzá tartozó edzés és gyakorlat adataival: a statisztikai számítások bemenete (entitás nélkül). */
public record SetRecord(
        Long workoutId,
        Instant startedAt,
        Instant endedAt,
        Short fatigue,
        Long exerciseId,
        String exerciseName,
        ExerciseCategory category,
        CardioType cardioType,
        BigDecimal weightKg,
        Short reps,
        Integer durationSec,
        Integer distanceM) {
}

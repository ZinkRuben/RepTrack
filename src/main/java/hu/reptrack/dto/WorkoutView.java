package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Egy edzés teljes nézete (a webes réteg ezt kapja, entitást nem). */
public record WorkoutView(
        Long id,
        String title,
        Instant startedAt,
        Instant endedAt,
        Short fatigue,
        String notes,
        Long version,
        List<ExerciseView> exercises) {

    public record ExerciseView(Long id, Long exerciseId, String exerciseName, ExerciseCategory category,
                               Short position, String notes, List<SetView> sets) {
    }

    public record SetView(Long id, Short position, BigDecimal weightKg, Short reps, Integer durationSec,
                          Integer distanceM, Short avgHeartRate, Short maxHeartRate) {
    }
}

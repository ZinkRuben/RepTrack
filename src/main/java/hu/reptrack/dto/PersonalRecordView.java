package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Egy felismert egyéni csúcs. A {@code value} jelentése a típustól függ: súly (kg), becsült 1RM (kg), szettvolumen (kg)
 * vagy tempó (perc/km). A {@code reps} csak MAX_WEIGHT_AT_REPS, a {@code distanceBand} csak BEST_PACE esetén van kitöltve.
 */
public record PersonalRecordView(
        Long id,
        Long exerciseId,
        String exerciseName,
        RecordType recordType,
        Short reps,
        DistanceBand distanceBand,
        BigDecimal value,
        LocalDate achievedOn,
        Long workoutId) {
}

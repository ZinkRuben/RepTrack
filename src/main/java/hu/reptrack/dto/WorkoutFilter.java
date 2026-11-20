package hu.reptrack.dto;

import java.time.Instant;

/**
 * Az edzéslista szűrői; bármelyik elhagyható. Az időszak {@code from} (benne van) és {@code to} (nincs benne) között
 * értendő az edzés kezdési időpontjára. A gyakorlat- és a kategóriaszűrő olyan edzéseket ad, amelyekben szerepel az adott
 * gyakorlat, illetve az adott kategória valamelyik gyakorlata.
 */
public record WorkoutFilter(Instant from, Instant to, Long exerciseId, ExerciseCategory category) {

    public static WorkoutFilter none() {
        return new WorkoutFilter(null, null, null, null);
    }
}

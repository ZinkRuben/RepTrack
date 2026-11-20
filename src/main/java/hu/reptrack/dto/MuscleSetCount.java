package hu.reptrack.dto;

import java.time.Instant;

/** Egy lezárt edzésben egy elsődleges izomcsoportra jutó szettek száma (a túlterhelési szabályok bemenete). */
public record MuscleSetCount(Long workoutId, Instant startedAt, MuscleGroup muscle, Long sets) {
}

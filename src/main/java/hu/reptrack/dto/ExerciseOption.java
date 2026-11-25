package hu.reptrack.dto;

/**
 * Egy választható gyakorlat az edzés és a sablon űrlapjához: a név, a kategória, az elsődleges izomcsoport (a
 * választó szűrőjéhez), hogy mely mezők rögzíthetők hozzá, és a képének kulcsa (lehet üres).
 */
public record ExerciseOption(
        Long id,
        String name,
        ExerciseCategory category,
        boolean tracksWeight,
        boolean tracksReps,
        boolean tracksDuration,
        boolean tracksDistance,
        boolean tracksHeartRate,
        boolean shared,
        MuscleGroup primaryMuscle,
        String imageKey) {
}

package hu.reptrack.dto;

/** Egy választható gyakorlat az edzés űrlapjához: a név és az, hogy mely mezők rögzíthetők hozzá. */
public record ExerciseOption(
        Long id,
        String name,
        ExerciseCategory category,
        boolean tracksWeight,
        boolean tracksReps,
        boolean tracksDuration,
        boolean tracksDistance,
        boolean tracksHeartRate,
        boolean shared) {
}

package hu.reptrack.dto;

import java.util.List;

/**
 * Egy gyakorlat nézete a listához (a webes réteg ezt kapja, entitást nem). A másodlagos izmok az enum sorrendjében.
 * {@code shared}: közös (adminisztrátor kezeli), különben a bejelentkezett felhasználó saját gyakorlata.
 * {@code inUse}: szerepel edzésben, sablonban vagy célban, ezért nem törölhető, csak archiválható.
 */
public record ExerciseView(
        Long id,
        String name,
        ExerciseCategory category,
        MuscleGroup primaryMuscle,
        List<MuscleGroup> secondaryMuscles,
        CardioType cardioType,
        String description,
        boolean tracksWeight,
        boolean tracksReps,
        boolean tracksDuration,
        boolean tracksDistance,
        boolean tracksHeartRate,
        boolean shared,
        boolean archived,
        boolean inUse) {
}

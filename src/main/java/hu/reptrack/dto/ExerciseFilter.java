package hu.reptrack.dto;

/**
 * A gyakorlatlista szűrői (UC4); bármelyik elhagyható. A név részszövegre, kis- és nagybetűtől függetlenül keres,
 * az izomcsoport az elsődleges és a másodlagos izmokra is illeszkedik.
 */
public record ExerciseFilter(String name, ExerciseCategory category, MuscleGroup muscle) {

    public static ExerciseFilter none() {
        return new ExerciseFilter(null, null, null);
    }
}

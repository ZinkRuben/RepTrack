package hu.reptrack.dto;

/**
 * A gyakorlatlista szűrői (UC4); bármelyik elhagyható. A név részszövegre, kis- és nagybetűtől függetlenül keres,
 * az izomcsoport az elsődleges és a másodlagos izmokra is illeszkedik. A forrás a közös vagy a saját gyakorlatokra
 * szűkít (null: mindkettő). {@code archived = true} esetén az archivált gyakorlatok látszanak (a saját archiváltak,
 * adminisztrátornak a közös archiváltak is), egyébként csak az aktívak.
 */
public record ExerciseFilter(String name, ExerciseCategory category, MuscleGroup muscle, ExerciseSource source,
                             Boolean archived) {

    public static ExerciseFilter none() {
        return new ExerciseFilter(null, null, null, null, null);
    }

    public boolean showArchived() {
        return Boolean.TRUE.equals(archived);
    }
}

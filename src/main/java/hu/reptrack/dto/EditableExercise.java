package hu.reptrack.dto;

/**
 * Egy gyakorlat a szerkesztő űrlaphoz: a kitöltött kérés (a verziószámmal) és az, hogy mi módosítható.
 * {@code inUse}: már szerepel edzésben, sablonban vagy célban, ezért a kategóriája és a rögzíthető mezői rögzítettek
 * (a korábbi szettek így érvényesek maradnak), és nem törölhető, csak archiválható.
 */
public record EditableExercise(Long id, boolean shared, boolean archived, boolean inUse, ExerciseRequest form) {
}

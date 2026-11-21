package hu.reptrack.dto;

/** Ilyen nevű gyakorlat már van (a közösek között, illetve a felhasználó saját gyakorlatai között). */
public class DuplicateExerciseNameException extends BusinessRuleException {

    public DuplicateExerciseNameException() {
        super("Ilyen nevű gyakorlat már van.");
    }
}

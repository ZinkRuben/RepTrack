package hu.reptrack.dto;

/** A saját fiók törlése előtt (UC14): mennyi adat törlődik a fiókkal együtt. Csak a tulajdonos látja. */
public record AccountDeletionSummary(
        long workouts,
        long ownExercises,
        long templates,
        long goals,
        long bodyWeightEntries,
        long personalRecords,
        boolean lastActiveAdmin) {
}

package hu.reptrack.web;

import hu.reptrack.dto.DistanceBand;
import hu.reptrack.dto.PersonalRecordView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Egy egyéni csúcs megjelenítése magyarul (a PR értékének jelentése a típustól függ). */
public record RecordFormatter(String exerciseName, String label, String value, LocalDate date, Long workoutId) {

    public static RecordFormatter of(PersonalRecordView r) {
        String label;
        String value;
        switch (r.recordType()) {
            case MAX_WEIGHT_AT_REPS -> {
                label = "Legnagyobb súly " + r.reps() + " ismétlésre";
                value = number(r.value()) + " kg × " + r.reps();
            }
            case ESTIMATED_1RM -> {
                label = "Becsült 1RM";
                value = number(r.value()) + " kg";
            }
            case SET_VOLUME -> {
                label = "Legnagyobb szettvolumen";
                value = number(r.value()) + " kg";
            }
            case BEST_PACE -> {
                label = "Legjobb tempó, " + band(r.distanceBand());
                value = pace(r.value()) + " /km";
            }
            default -> throw new IllegalStateException("Ismeretlen rekordtípus: " + r.recordType());
        }
        return new RecordFormatter(r.exerciseName(), label, value, r.achievedOn(), r.workoutId());
    }

    private static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /** A tempó (perc/km, tizedes) perc:másodperc alakban; a másodperc kerekítése nem lehet 60. */
    static String pace(BigDecimal minutes) {
        long totalSeconds = minutes.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValue();
        return totalSeconds / 60 + ":" + String.format("%02d", totalSeconds % 60);
    }

    private static String band(DistanceBand band) {
        return switch (band) {
            case ONE_K -> "1 km";
            case FIVE_K -> "5 km";
            case TEN_K -> "10 km";
            case HALF_MARATHON -> "félmaraton";
            case MARATHON -> "maraton";
        };
    }
}

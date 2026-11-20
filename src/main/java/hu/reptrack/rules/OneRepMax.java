package hu.reptrack.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Becsült egyismétléses maximum (1RM) az Epley-képlettel: súly × (1 + ismétlésszám / 30). */
public final class OneRepMax {

    /** Ennél több ismétléses szettből a becslés megbízhatatlan, ezért nem számít bele a PR-be és a görbébe. */
    public static final int MAX_RELIABLE_REPS = 10;

    private OneRepMax() {
    }

    public static boolean isReliable(Short reps) {
        return reps != null && reps >= 1 && reps <= MAX_RELIABLE_REPS;
    }

    public static BigDecimal estimate(BigDecimal weightKg, int reps) {
        BigDecimal factor = BigDecimal.ONE.add(BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 10, RoundingMode.HALF_UP));
        return weightKg.multiply(factor).setScale(1, RoundingMode.HALF_UP);
    }
}

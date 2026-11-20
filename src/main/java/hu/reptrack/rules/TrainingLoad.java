package hu.reptrack.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/** Az edzésterhelés: fáradtságérték (1–10) × edzésidő percben. Csak lezárt edzésre értelmezett. */
public final class TrainingLoad {

    private TrainingLoad() {
    }

    /** Az edzés időtartama percben, egy tizedesre kerekítve. */
    public static BigDecimal minutes(Instant startedAt, Instant endedAt) {
        long seconds = Duration.between(startedAt, endedAt).getSeconds();
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(short fatigue, BigDecimal minutes) {
        return minutes.multiply(BigDecimal.valueOf(fatigue)).setScale(1, RoundingMode.HALF_UP);
    }
}

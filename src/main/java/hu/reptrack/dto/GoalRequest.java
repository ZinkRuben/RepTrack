package hu.reptrack.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Új edzéscél (UC2). A célérték egysége a céltípustól függ: testtömeg-célnál (MUSCLE_GAIN, WEIGHT_LOSS) a cél
 * testtömeg kg-ban, erő-célnál (STRENGTH) a gyakorlatban emelni kívánt súly kg-ban, állóképességi célnál
 * (ENDURANCE) a {@link EnduranceMetric mérték} szerint km vagy perc. Az „idő adott távon” célnál a táv a
 * {@code targetDistanceM}-ben van (méter), a célérték pedig a célidő percben. Mind a gyakorlat, mind a célérték és a
 * határidő elhagyható, de a haladás csak ezekkel együtt számolható.
 */
public record GoalRequest(
        @NotNull GoalType type,
        Long exerciseId,
        @DecimalMin("0.01") @DecimalMax("1000") BigDecimal targetValue,
        LocalDate deadline,
        EnduranceMetric enduranceMetric,
        @Positive @Max(1_000_000) Integer targetDistanceM) {

    /** Céltípus mérték és táv nélkül (testtömeg- és erő-célhoz). */
    public GoalRequest(GoalType type, Long exerciseId, BigDecimal targetValue, LocalDate deadline) {
        this(type, exerciseId, targetValue, deadline, null, null);
    }
}

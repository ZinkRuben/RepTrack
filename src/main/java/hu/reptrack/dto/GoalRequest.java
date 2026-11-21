package hu.reptrack.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Új edzéscél (UC2). A célérték testtömeg-célnál (MUSCLE_GAIN, WEIGHT_LOSS) a cél testtömeg kg-ban, erő-célnál
 * (STRENGTH) a gyakorlatban emelni kívánt súly kg-ban. Mind a gyakorlat, mind a célérték és a határidő elhagyható,
 * de a haladás csak ezekkel együtt számolható.
 */
public record GoalRequest(
        @NotNull GoalType type,
        Long exerciseId,
        @DecimalMin("0.01") @DecimalMax("1000") BigDecimal targetValue,
        LocalDate deadline) {
}

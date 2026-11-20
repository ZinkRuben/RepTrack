package hu.reptrack.dto;

import java.math.BigDecimal;

/**
 * Az edzéscél felé tett haladás. A {@code percent} 0 és 100 közé vágott érték; null, ha nem számítható
 * (nincs célérték, nincs mérés, vagy a célfajtához még nincs haladási szabály, pl. ENDURANCE).
 */
public record GoalProgress(
        Long goalId,
        GoalType type,
        Long exerciseId,
        BigDecimal start,
        BigDecimal current,
        BigDecimal target,
        BigDecimal percent) {
}

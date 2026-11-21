package hu.reptrack.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Egy testtömeg-mérés rögzítése (UC3). Az adott napra már rögzített mérést felülírja. */
public record BodyWeightRequest(
        @NotNull LocalDate date,
        @NotNull @DecimalMin("20") @DecimalMax("500") BigDecimal weightKg) {
}

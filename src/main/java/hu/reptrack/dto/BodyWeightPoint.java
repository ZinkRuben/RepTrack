package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Egy testtömeg-mérés. */
public record BodyWeightPoint(LocalDate date, BigDecimal weightKg) {
}

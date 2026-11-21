package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BodyWeightView(Long id, LocalDate date, BigDecimal weightKg) {
}

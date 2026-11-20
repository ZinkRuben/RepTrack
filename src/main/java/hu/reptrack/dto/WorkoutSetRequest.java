package hu.reptrack.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Egy szett adatai. Hogy mely mezők kötelezők vagy tiltottak, a gyakorlat beállításaitól függ (a service ellenőrzi). */
public record WorkoutSetRequest(
        @DecimalMin("0") @DecimalMax("500") BigDecimal weightKg,
        @Min(1) @Max(100) Short reps,
        @Positive Integer durationSec,
        @Positive Integer distanceM,
        @Min(30) @Max(230) Short avgHeartRate,
        @Min(30) @Max(230) Short maxHeartRate) {
}

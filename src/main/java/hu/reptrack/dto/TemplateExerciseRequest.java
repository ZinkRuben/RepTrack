package hu.reptrack.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Egy gyakorlat a sablonban: hány szettet tervez a felhasználó, és opcionálisan milyen értékekkel. Csak a szettek
 * száma kötelező (a terv lehet laza); hogy mely mezők adhatók meg, a gyakorlat beállításaitól függ (a service
 * ellenőrzi). A korlátok ugyanazok, mint a rögzített szettnél, az időtartam másodperc, a táv méter.
 */
public record TemplateExerciseRequest(
        @NotNull Long exerciseId,
        @NotNull @Min(1) @Max(20) Short plannedSets,
        @Min(1) @Max(100) Short plannedReps,
        @DecimalMin("0") @DecimalMax("500") BigDecimal plannedWeightKg,
        @Positive Integer plannedDurationSec,
        @Positive Integer plannedDistanceM) {
}

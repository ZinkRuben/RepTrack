package hu.reptrack.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A profil módosítása (UC2): a személyes adatok és a túlterhelési figyelmeztetések küszöbei. A {@code version} a
 * korábban megkapott verzió (optimista zárolás).
 */
public record ProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Past LocalDate birthDate,
        @Min(50) @Max(260) Short heightCm,
        @NotNull FitnessLevel fitnessLevel,
        @NotNull @DecimalMin("1.05") @DecimalMax("3.00") BigDecimal loadRatioThreshold,
        @NotNull @Min(1) @Max(30) Short muscleSetsThreshold,
        @NotNull @Min(1) @Max(10) Short fatigueThreshold,
        @NotNull Long version) {
}

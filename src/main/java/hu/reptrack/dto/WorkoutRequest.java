package hu.reptrack.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Edzés létrehozásának és módosításának adatai. A {@code version} létrehozáskor üres, módosításkor a
 * korábban megkapott érték (optimista zárolás). A fáradtság kötelező, ha az edzés le van zárva ({@code endedAt}).
 */
public record WorkoutRequest(
        @NotBlank @Size(max = 120) String title,
        @NotNull Instant startedAt,
        Instant endedAt,
        @Min(1) @Max(10) Short fatigue,
        @Size(max = 2000) String notes,
        Long version,
        List<@Valid WorkoutExerciseRequest> exercises) {

    public WorkoutRequest {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }
}

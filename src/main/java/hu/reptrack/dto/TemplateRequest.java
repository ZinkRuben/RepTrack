package hu.reptrack.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Edzéssablon létrehozásának és módosításának adatai (UC15). A gyakorlatok sorrendjét a lista sorrendje adja; a
 * sablonban legalább egy gyakorlat legyen (ezt a service ellenőrzi).
 */
public record TemplateRequest(
        @NotBlank @Size(max = 120) String name,
        List<@Valid TemplateExerciseRequest> exercises) {

    public TemplateRequest {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }
}

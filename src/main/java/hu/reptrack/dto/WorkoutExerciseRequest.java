package hu.reptrack.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Egy gyakorlat az edzésen belül, a szettjeivel. A sorrendet a lista sorrendje adja. */
public record WorkoutExerciseRequest(
        @NotNull Long exerciseId,
        @Size(max = 2000) String notes,
        List<@Valid WorkoutSetRequest> sets) {

    public WorkoutExerciseRequest {
        sets = sets == null ? List.of() : List.copyOf(sets);
    }
}

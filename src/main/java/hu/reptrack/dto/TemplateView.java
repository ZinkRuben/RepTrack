package hu.reptrack.dto;

import java.math.BigDecimal;
import java.util.List;

/** Egy edzéssablon teljes nézete (a webes réteg ezt kapja, entitást nem). */
public record TemplateView(Long id, String name, List<ExerciseView> exercises) {

    /**
     * A sablon egy gyakorlata a tervezett értékekkel. Az {@code archived} jelzi, ha a gyakorlatot azóta archiválták:
     * a sablonban maradhat, de belőle új edzés nem indítható vele.
     */
    public record ExerciseView(Long exerciseId, String exerciseName, ExerciseCategory category, boolean archived,
                               Short position, Short plannedSets, Short plannedReps, BigDecimal plannedWeightKg,
                               Integer plannedDurationSec, Integer plannedDistanceM) {
    }
}

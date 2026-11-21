package hu.reptrack.web;

import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.EditableExercise;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseRequest;
import hu.reptrack.dto.MuscleGroup;
import org.springframework.ui.Model;

/** A gyakorlat-űrlap (exercise-form.html) közös modellje a saját és a közös (admin) gyakorlatokhoz. */
final class ExerciseFormSupport {

    private ExerciseFormSupport() {
    }

    /** Új gyakorlat kiinduló értékei: súlyzós, súly és ismétlés. */
    static ExerciseRequest defaults() {
        ExerciseRequest form = new ExerciseRequest();
        form.setCategory(ExerciseCategory.WEIGHTS);
        form.setTracksWeight(true);
        form.setTracksReps(true);
        return form;
    }

    /**
     * @param basePath  "/exercises" (saját) vagy "/admin/exercises" (közös)
     * @param editing   a szerkesztett gyakorlat, vagy null új gyakorlatnál
     */
    static String page(Model model, String basePath, boolean shared, EditableExercise editing) {
        Long id = editing == null ? null : editing.id();
        model.addAttribute("formAction", id == null ? basePath : basePath + "/" + id);
        model.addAttribute("shared", shared);
        model.addAttribute("editing", id != null);
        model.addAttribute("locked", editing != null && editing.inUse());
        model.addAttribute("categories", ExerciseCategory.values());
        model.addAttribute("cardioTypes", CardioType.values());
        model.addAttribute("muscles", MuscleGroup.values());
        return "exercise-form";
    }
}

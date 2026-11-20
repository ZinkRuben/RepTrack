package hu.reptrack.web;

import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseFilter;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.service.ExerciseService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Gyakorlatok böngészése (UC4). A szűrők GET paraméterek, így a szűrt lista linkként is megosztható. */
@Controller
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping("/exercises")
    public String list(@ModelAttribute("filter") ExerciseFilter filter, Model model) {
        model.addAttribute("exercises", exerciseService.search(filter));
        model.addAttribute("categories", ExerciseCategory.values());
        model.addAttribute("muscles", MuscleGroup.values());
        return "exercises";
    }
}

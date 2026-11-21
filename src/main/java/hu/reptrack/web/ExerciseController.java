package hu.reptrack.web;

import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.DuplicateExerciseNameException;
import hu.reptrack.dto.EditableExercise;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseFilter;
import hu.reptrack.dto.ExerciseRequest;
import hu.reptrack.dto.ExerciseSource;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.StaleDataException;
import hu.reptrack.service.AuthenticatedUser;
import hu.reptrack.service.ExerciseService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Gyakorlatok böngészése (UC4) és a saját gyakorlatok kezelése (UC5). A közös gyakorlatokat az
 * {@link AdminExerciseController} kezeli. A szűrők GET paraméterek, így a szűrt lista linkként is megosztható.
 */
@Controller
@RequestMapping("/exercises")
public class ExerciseController {

    static final String BASE = "/exercises";

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user, @ModelAttribute("filter") ExerciseFilter filter,
                       Model model) {
        model.addAttribute("exercises", exerciseService.search(user.getId(), filter));
        model.addAttribute("categories", ExerciseCategory.values());
        model.addAttribute("muscles", MuscleGroup.values());
        model.addAttribute("sources", ExerciseSource.values());
        return "exercises";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", ExerciseFormSupport.defaults());
        return ExerciseFormSupport.page(model, BASE, false, null);
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AuthenticatedUser user,
                         @Valid @ModelAttribute("form") ExerciseRequest form, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                exerciseService.createOwn(user.getId(), form);
                FlashMessages.success(redirect, "A(z) „" + form.getName().strip() + "” gyakorlat létrehozva.");
                return "redirect:/exercises?source=OWN";
            } catch (DuplicateExerciseNameException e) {
                result.rejectValue("name", "duplicate", "Ilyen nevű saját gyakorlatod már van.");
            }
        }
        return ExerciseFormSupport.page(model, BASE, false, null);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        EditableExercise editing = exerciseService.getOwnForEdit(user.getId(), id);
        model.addAttribute("form", editing.form());
        return ExerciseFormSupport.page(model, BASE, false, editing);
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @Valid @ModelAttribute("form") ExerciseRequest form, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                exerciseService.updateOwn(user.getId(), id, form);
                FlashMessages.success(redirect, "A(z) „" + form.getName().strip() + "” gyakorlat mentve.");
                return "redirect:/exercises?source=OWN";
            } catch (DuplicateExerciseNameException e) {
                result.rejectValue("name", "duplicate", "Ilyen nevű saját gyakorlatod már van.");
            } catch (StaleDataException e) {
                FlashMessages.error(redirect, e.getMessage());
                return "redirect:/exercises/" + id + "/edit";
            }
        }
        return ExerciseFormSupport.page(model, BASE, false, exerciseService.getOwnForEdit(user.getId(), id));
    }

    @PostMapping("/{id}/archive")
    public String archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          RedirectAttributes redirect) {
        exerciseService.setOwnArchived(user.getId(), id, true);
        FlashMessages.success(redirect, "A gyakorlat archiválva: új edzésbe nem vehető fel, a korábbiakban megmarad. "
                + "Az „Archiváltak” szűrővel visszaállítható.");
        return "redirect:/exercises?source=OWN";
    }

    @PostMapping("/{id}/restore")
    public String restore(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                          RedirectAttributes redirect) {
        exerciseService.setOwnArchived(user.getId(), id, false);
        FlashMessages.success(redirect, "A gyakorlat visszaállítva.");
        return "redirect:/exercises?source=OWN";
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         RedirectAttributes redirect) {
        try {
            exerciseService.deleteOwn(user.getId(), id);
            FlashMessages.success(redirect, "A gyakorlat törölve.");
        } catch (BusinessRuleException e) {
            FlashMessages.error(redirect, e.getMessage());
        }
        return "redirect:/exercises?source=OWN";
    }
}

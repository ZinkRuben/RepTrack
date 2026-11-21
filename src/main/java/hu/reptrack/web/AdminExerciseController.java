package hu.reptrack.web;

import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.DuplicateExerciseNameException;
import hu.reptrack.dto.EditableExercise;
import hu.reptrack.dto.ExerciseRequest;
import hu.reptrack.dto.StaleDataException;
import hu.reptrack.service.ExerciseService;
import jakarta.validation.Valid;
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
 * A közös gyakorlatadatbázis karbantartása (UC6): felvétel, módosítás, archiválás, visszaállítás, törlés.
 * Az /admin/** utakat a SecurityConfig csak adminisztrátornak engedi, a szolgáltatás metódusait pedig
 * {@code @PreAuthorize} is védi. A lista a közös /exercises oldal (adminisztrátornak ott jelennek meg a műveletek).
 */
@Controller
@RequestMapping("/admin/exercises")
public class AdminExerciseController {

    static final String BASE = "/admin/exercises";

    private final ExerciseService exerciseService;

    public AdminExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("form", ExerciseFormSupport.defaults());
        return ExerciseFormSupport.page(model, BASE, true, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ExerciseRequest form, BindingResult result, Model model,
                         RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                exerciseService.createShared(form);
                FlashMessages.success(redirect, "A(z) „" + form.getName().strip() + "” közös gyakorlat létrehozva.");
                return "redirect:/exercises?source=SHARED";
            } catch (DuplicateExerciseNameException e) {
                result.rejectValue("name", "duplicate", "Ilyen nevű közös gyakorlat már van.");
            }
        }
        return ExerciseFormSupport.page(model, BASE, true, null);
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        EditableExercise editing = exerciseService.getSharedForEdit(id);
        model.addAttribute("form", editing.form());
        return ExerciseFormSupport.page(model, BASE, true, editing);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ExerciseRequest form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                exerciseService.updateShared(id, form);
                FlashMessages.success(redirect, "A(z) „" + form.getName().strip() + "” közös gyakorlat mentve.");
                return "redirect:/exercises?source=SHARED";
            } catch (DuplicateExerciseNameException e) {
                result.rejectValue("name", "duplicate", "Ilyen nevű közös gyakorlat már van.");
            } catch (StaleDataException e) {
                FlashMessages.error(redirect, e.getMessage());
                return "redirect:/admin/exercises/" + id + "/edit";
            }
        }
        return ExerciseFormSupport.page(model, BASE, true, exerciseService.getSharedForEdit(id));
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable Long id, RedirectAttributes redirect) {
        exerciseService.setSharedArchived(id, true);
        FlashMessages.success(redirect, "A közös gyakorlat archiválva: új edzésbe senki nem veheti fel, a korábbiakban "
                + "megmarad. Az „Archiváltak” szűrővel visszaállítható.");
        return "redirect:/exercises?source=SHARED";
    }

    @PostMapping("/{id}/restore")
    public String restore(@PathVariable Long id, RedirectAttributes redirect) {
        exerciseService.setSharedArchived(id, false);
        FlashMessages.success(redirect, "A közös gyakorlat visszaállítva.");
        return "redirect:/exercises?source=SHARED";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            exerciseService.deleteShared(id);
            FlashMessages.success(redirect, "A közös gyakorlat törölve.");
        } catch (BusinessRuleException e) {
            FlashMessages.error(redirect, e.getMessage());
        }
        return "redirect:/exercises?source=SHARED";
    }
}

package hu.reptrack.web;

import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.DuplicateTemplateNameException;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.TemplateView;
import hu.reptrack.service.AuthenticatedUser;
import hu.reptrack.service.TemplateService;
import hu.reptrack.service.WorkoutService;
import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Az edzéssablonok oldalai (UC15): lista, új sablon, szerkesztés, törlés. A sablonból indított edzést a
 * {@link WorkoutController} kezeli ({@code /workouts/new?template=...}). A gyakorlatválasztó ugyanazokat a
 * gyakorlatokat kínálja, mint az edzés űrlapja.
 */
@Controller
@RequestMapping("/templates")
public class TemplateController {

    private static final String VALIDATION_MESSAGE = "A megadott adatok érvénytelenek. Ellenőrizd a sablon nevét "
            + "(legfeljebb 120 karakter), a szettek számát (1–20), az ismétlést (1–100) és a súlyt (0–500 kg).";

    private final TemplateService templates;
    private final WorkoutService workouts;

    public TemplateController(TemplateService templates, WorkoutService workouts) {
        this.templates = templates;
        this.workouts = workouts;
    }

    // ---- lista ----

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("templates", templates.list(user.getId()).stream().map(TemplateController::toItem).toList());
        return "templates";
    }

    /** Egy sablon a listában. */
    public record Item(Long id, String name, List<Line> lines, boolean hasArchived) {
    }

    /** A sablon egy gyakorlata a listában, a terv szövegével (pl. „5 × 5 ismétlés · 100 kg”). */
    public record Line(String exerciseName, ExerciseCategory category, boolean archived, String plan) {
    }

    private static Item toItem(TemplateView t) {
        List<Line> lines = t.exercises().stream()
                .map(e -> new Line(e.exerciseName(), e.category(), e.archived(), plan(e)))
                .toList();
        return new Item(t.id(), t.name(), lines, lines.stream().anyMatch(Line::archived));
    }

    /** A tervezett értékek egy sorban; a meg nem adott értékek kimaradnak. */
    static String plan(TemplateView.ExerciseView e) {
        List<String> parts = new ArrayList<>();
        String head = e.plannedSets() + " szett";
        if (e.plannedReps() != null) {
            head = e.plannedSets() + " × " + e.plannedReps() + " ismétlés";
        } else if (e.plannedDurationSec() != null) {
            head = e.plannedSets() + " × " + FormValues.display(FormValues.minutes(e.plannedDurationSec())) + " perc";
        }
        parts.add(head);
        if (e.plannedWeightKg() != null) {
            parts.add(FormValues.display(e.plannedWeightKg()) + " kg");
        }
        if (e.plannedReps() != null && e.plannedDurationSec() != null) {
            parts.add(FormValues.display(FormValues.minutes(e.plannedDurationSec())) + " perc");
        }
        if (e.plannedDistanceM() != null) {
            parts.add(FormValues.display(FormValues.kilometers(e.plannedDistanceM())) + " km");
        }
        return String.join(" · ", parts);
    }

    // ---- új sablon ----

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        return formPage(user, new TemplateForm(), null, model);
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AuthenticatedUser user, @ModelAttribute("form") TemplateForm form,
                         Model model, RedirectAttributes redirect) {
        try {
            TemplateView created = templates.create(user.getId(), form.toRequest());
            FlashMessages.success(redirect, "A(z) „" + created.name() + "” sablon mentve.");
            return "redirect:/templates";
        } catch (BusinessRuleException | ConstraintViolationException ex) {
            return formError(user, form, null, ex, model);
        }
    }

    // ---- szerkesztés, törlés ----

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        return formPage(user, TemplateForm.from(templates.get(user.getId(), id)), id, model);
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @ModelAttribute("form") TemplateForm form, Model model,
                         RedirectAttributes redirect) {
        try {
            TemplateView updated = templates.update(user.getId(), id, form.toRequest());
            FlashMessages.success(redirect, "A(z) „" + updated.name() + "” sablon mentve.");
            return "redirect:/templates";
        } catch (BusinessRuleException | ConstraintViolationException ex) {
            return formError(user, form, id, ex, model);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         RedirectAttributes redirect) {
        templates.delete(user.getId(), id);
        FlashMessages.success(redirect, "A sablon törölve. A belőle korábban rögzített edzések megmaradnak.");
        return "redirect:/templates";
    }

    // ---- segédek ----

    private String formError(AuthenticatedUser user, TemplateForm form, Long templateId, RuntimeException ex, Model model) {
        model.addAttribute(FlashMessages.ERROR, ex instanceof ConstraintViolationException ? VALIDATION_MESSAGE : ex.getMessage());
        model.addAttribute("nameTaken", ex instanceof DuplicateTemplateNameException);
        return formPage(user, form, templateId, model);
    }

    /** Az űrlap kirajzolása: a választható gyakorlatok és a kitöltött sorok a szkript számára egyszerű struktúrában. */
    String formPage(AuthenticatedUser user, TemplateForm form, Long templateId, Model model) {
        List<Long> usedIds = form.getExercises().stream().map(TemplateForm.Row::getExerciseId)
                .filter(Objects::nonNull).toList();
        model.addAttribute("form", form);
        model.addAttribute("templateId", templateId);
        model.addAttribute("catalog", ExerciseCatalog.of(workouts.exerciseOptions(user.getId(), usedIds)));
        model.addAttribute("initialRows", form.getExercises().stream().map(TemplateController::rowEntry).toList());
        return "template-form";
    }

    private static Map<String, Object> rowEntry(TemplateForm.Row row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exerciseId", row.getExerciseId() == null ? null : String.valueOf(row.getExerciseId()));
        m.put("plannedSets", FormValues.text(row.getPlannedSets()));
        m.put("plannedReps", FormValues.text(row.getPlannedReps()));
        m.put("plannedWeightKg", FormValues.text(row.getPlannedWeightKg()));
        m.put("plannedDurationMin", FormValues.text(row.getPlannedDurationMin()));
        m.put("plannedDistanceKm", FormValues.text(row.getPlannedDistanceKm()));
        return m;
    }
}

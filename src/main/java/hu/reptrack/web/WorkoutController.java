package hu.reptrack.web;

import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.WorkoutFilter;
import hu.reptrack.dto.WorkoutSummary;
import hu.reptrack.dto.WorkoutView;
import hu.reptrack.service.AuthenticatedUser;
import hu.reptrack.service.OverloadService;
import hu.reptrack.service.PersonalRecordService;
import hu.reptrack.service.WorkoutService;
import jakarta.validation.ConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Az edzésnapló oldalai (UC7, UC8, UC9): lista szűrőkkel, új edzés, részletek, szerkesztés, törlés és másolás.
 * A mentés után a részletek oldal mutatja az edzésben elért új csúcsokat és a túlterhelési figyelmeztetéseket.
 * Az időpontokat a felhasználó helyi idejében (a beállított időzónában) kezeli, a szolgáltatás UTC-t kap.
 */
@Controller
@RequestMapping("/workouts")
public class WorkoutController {

    private static final int PAGE_SIZE = 20;
    private static final String VALIDATION_MESSAGE = "A megadott adatok érvénytelenek. Ellenőrizd az edzés nevét és az "
            + "időpontot, a súlyt (0–500 kg), az ismétlést (1–100), a pulzust (30–230) és a fáradtságot (1–10).";

    private final WorkoutService workouts;
    private final PersonalRecordService personalRecords;
    private final OverloadService overload;
    private final ZoneId zone;

    public WorkoutController(WorkoutService workouts, PersonalRecordService personalRecords, OverloadService overload,
                             @Value("${reptrack.timezone:Europe/Budapest}") String timezone) {
        this.workouts = workouts;
        this.personalRecords = personalRecords;
        this.overload = overload;
        this.zone = ZoneId.of(timezone);
    }

    // ---- lista ----

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser user,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) Long exerciseId,
                       @RequestParam(required = false) ExerciseCategory category,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        if (from != null && to != null && from.isAfter(to)) {
            model.addAttribute(FlashMessages.ERROR, "A kezdő dátum nem lehet a záró dátum után, a szűrőt figyelmen kívül hagytam.");
            from = null;
            to = null;
        }
        WorkoutFilter filter = new WorkoutFilter(
                from == null ? null : from.atStartOfDay(zone).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant(),
                exerciseId, category);
        Page<WorkoutSummary> result = workouts.list(user.getId(), filter, PageRequest.of(Math.max(page, 0), PAGE_SIZE));

        model.addAttribute("items", result.getContent().stream().map(this::toItem).toList());
        model.addAttribute("pageNumber", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("exerciseId", exerciseId);
        model.addAttribute("category", category);
        model.addAttribute("categories", ExerciseCategory.values());
        model.addAttribute("options", workouts.exerciseOptions(user.getId(), List.of()));
        model.addAttribute("openWarnings", overload.list(user.getId(), false));
        return "workouts";
    }

    /** Egy sor a listában (helyi idővel és az edzés hosszával). */
    public record Item(Long id, String title, LocalDateTime startedAt, Long minutes, Short fatigue,
                       Integer exerciseCount, boolean closed) {
    }

    private Item toItem(WorkoutSummary s) {
        Long minutes = s.endedAt() == null ? null : Duration.between(s.startedAt(), s.endedAt()).toMinutes();
        return new Item(s.id(), s.title(), local(s.startedAt()), minutes, s.fatigue(), s.exerciseCount(), s.endedAt() != null);
    }

    // ---- új edzés ----

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        return formPage(user, WorkoutForm.blank(zone), null, model);
    }

    @PostMapping
    public String create(@AuthenticationPrincipal AuthenticatedUser user, @ModelAttribute("form") WorkoutForm form,
                         Model model, RedirectAttributes redirect) {
        try {
            WorkoutView created = workouts.create(user.getId(), form.toRequest(zone));
            FlashMessages.success(redirect, "Az edzés mentve.");
            return "redirect:/workouts/" + created.id();
        } catch (BusinessRuleException | ConstraintViolationException ex) {
            model.addAttribute(FlashMessages.ERROR, messageOf(ex));
            return formPage(user, form, null, model);
        }
    }

    // ---- részletek ----

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        WorkoutView workout = workouts.get(user.getId(), id);
        model.addAttribute("workout", workout);
        model.addAttribute("startedAt", local(workout.startedAt()));
        model.addAttribute("endedAt", workout.endedAt() == null ? null : local(workout.endedAt()));
        model.addAttribute("minutes", workout.endedAt() == null ? null
                : Duration.between(workout.startedAt(), workout.endedAt()).toMinutes());
        model.addAttribute("records", personalRecords.achievedIn(user.getId(), id).stream().map(RecordFormatter::of).toList());
        model.addAttribute("warnings", overload.forWorkout(user.getId(), id));
        return "workout";
    }

    // ---- szerkesztés ----

    @GetMapping("/{id}/edit")
    public String editForm(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, Model model) {
        return formPage(user, WorkoutForm.from(workouts.get(user.getId(), id), zone), id, model);
    }

    @PostMapping("/{id}")
    public String update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                         @ModelAttribute("form") WorkoutForm form, Model model, RedirectAttributes redirect) {
        try {
            workouts.update(user.getId(), id, form.toRequest(zone));
            FlashMessages.success(redirect, "Az edzés módosítva.");
            return "redirect:/workouts/" + id;
        } catch (BusinessRuleException | ConstraintViolationException ex) {
            model.addAttribute(FlashMessages.ERROR, messageOf(ex));
            return formPage(user, form, id, model);
        }
    }

    // ---- törlés, másolás ----

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, RedirectAttributes redirect) {
        workouts.delete(user.getId(), id);
        FlashMessages.success(redirect, "Az edzés törölve.");
        return "redirect:/workouts";
    }

    @PostMapping("/{id}/copy")
    public String copy(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, RedirectAttributes redirect) {
        WorkoutView copy = workouts.copy(user.getId(), id);
        FlashMessages.success(redirect, "Másolat létrehozva. Igazítsd az adatokat, majd add meg a befejezést és a fáradtságot.");
        return "redirect:/workouts/" + copy.id() + "/edit";
    }

    // ---- segédek ----

    /** Az űrlap kirajzolása: a választható gyakorlatok és a kitöltött blokkok a szkript számára egyszerű struktúrában. */
    private String formPage(AuthenticatedUser user, WorkoutForm form, Long workoutId, Model model) {
        List<Long> usedIds = form.getExercises().stream().map(WorkoutForm.ExerciseBlock::getExerciseId)
                .filter(java.util.Objects::nonNull).toList();
        model.addAttribute("form", form);
        model.addAttribute("workoutId", workoutId);
        model.addAttribute("catalog", ExerciseCatalog.of(workouts.exerciseOptions(user.getId(), usedIds)));
        model.addAttribute("initialBlocks", form.getExercises().stream().map(this::blockEntry).toList());
        return "workout-form";
    }

    private Map<String, Object> blockEntry(WorkoutForm.ExerciseBlock block) {
        List<Map<String, Object>> sets = new ArrayList<>();
        for (WorkoutForm.SetRow s : block.getSets()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("weightKg", FormValues.text(s.getWeightKg()));
            row.put("reps", FormValues.text(s.getReps()));
            row.put("durationMin", FormValues.text(s.getDurationMin()));
            row.put("distanceKm", FormValues.text(s.getDistanceKm()));
            row.put("avgHeartRate", FormValues.text(s.getAvgHeartRate()));
            row.put("maxHeartRate", FormValues.text(s.getMaxHeartRate()));
            sets.add(row);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exerciseId", block.getExerciseId() == null ? null : String.valueOf(block.getExerciseId()));
        m.put("notes", block.getNotes());
        m.put("sets", sets);
        return m;
    }

    private LocalDateTime local(Instant instant) {
        return instant.atZone(zone).toLocalDateTime();
    }

    private static String messageOf(RuntimeException ex) {
        return ex instanceof ConstraintViolationException ? VALIDATION_MESSAGE : ex.getMessage();
    }
}

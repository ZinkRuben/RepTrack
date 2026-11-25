package hu.reptrack.service;

import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.TemplateExercise;
import hu.reptrack.domain.User;
import hu.reptrack.domain.WorkoutTemplate;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.DuplicateTemplateNameException;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.TemplateExerciseRequest;
import hu.reptrack.dto.TemplateRequest;
import hu.reptrack.dto.TemplateView;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.repository.WorkoutTemplateRepository;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Edzéssablonok (UC15): sablon mentése, módosítása, törlése és lekérdezése.
 *
 * <p>A sablon terv, nem edzésadat: a PR-eket, a statisztikát és a túlterhelési figyelmeztetéseket nem érinti. Edzést a
 * webes réteg indít belőle (kitölti az új edzés űrlapját), és az edzés mentése a szokásos {@link WorkoutService}-en
 * át történik. A gyakorlatokra ugyanazok a szabályok vonatkoznak, mint az edzésnél: csak látható (közös vagy saját),
 * nem archivált gyakorlat vehető fel, és csak a gyakorlathoz beállított mezők adhatók meg.
 */
@Service
@Validated
@Transactional
public class TemplateService {

    private final WorkoutTemplateRepository templates;
    private final ExerciseRepository exercises;
    private final UserRepository users;

    public TemplateService(WorkoutTemplateRepository templates, ExerciseRepository exercises, UserRepository users) {
        this.templates = templates;
        this.exercises = exercises;
        this.users = users;
    }

    /** A felhasználó sablonjai név szerint. */
    @Transactional(readOnly = true)
    public List<TemplateView> list(Long userId) {
        return templates.findByUserIdOrderByName(userId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public TemplateView get(Long userId, Long templateId) {
        return toView(load(userId, templateId));
    }

    public TemplateView create(Long userId, @Valid TemplateRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("A felhasználó nem található."));
        if (templates.existsByUserIdAndNameIgnoreCase(userId, request.name().strip())) {
            throw new DuplicateTemplateNameException();
        }
        WorkoutTemplate template = new WorkoutTemplate();
        template.setUser(user);
        apply(template, userId, request);
        templates.saveAndFlush(template);
        return toView(template);
    }

    /** A sablon módosítása: a név és a gyakorlatok a kérés szerint lecserélődnek. */
    public TemplateView update(Long userId, Long templateId, @Valid TemplateRequest request) {
        WorkoutTemplate template = load(userId, templateId);
        if (templates.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, request.name().strip(), templateId)) {
            throw new DuplicateTemplateNameException();
        }
        apply(template, userId, request);
        templates.flush();
        return toView(template);
    }

    public void delete(Long userId, Long templateId) {
        templates.delete(load(userId, templateId));
    }

    // ---- belső segédek ----

    private WorkoutTemplate load(Long userId, Long templateId) {
        return templates.findByIdAndUserId(templateId, userId)
                .orElseThrow(() -> new NotFoundException("A sablon nem található."));
    }

    private void apply(WorkoutTemplate template, Long userId, TemplateRequest request) {
        if (request.exercises().isEmpty()) {
            throw new BusinessRuleException("A sablonban legalább egy gyakorlat legyen.");
        }
        template.setName(request.name().strip());

        Set<Long> alreadyInTemplate = new HashSet<>();
        for (TemplateExercise existing : template.getExercises()) {
            alreadyInTemplate.add(existing.getExercise().getId());
        }
        List<Long> ids = request.exercises().stream().map(TemplateExerciseRequest::exerciseId).distinct().toList();
        Map<Long, Exercise> byId = exercises.findAllById(ids).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));

        template.getExercises().clear();
        short position = 1;
        for (TemplateExerciseRequest row : request.exercises()) {
            Exercise exercise = byId.get(row.exerciseId());
            checkUsable(exercise, userId, alreadyInTemplate);
            validatePlan(exercise, row);

            TemplateExercise te = new TemplateExercise();
            te.setExercise(exercise);
            te.setPosition(position++);
            te.setPlannedSets(row.plannedSets());
            te.setPlannedReps(row.plannedReps());
            te.setPlannedWeightKg(row.plannedWeightKg());
            te.setPlannedDurationSec(row.plannedDurationSec());
            te.setPlannedDistanceM(row.plannedDistanceM());
            template.addExercise(te);
        }
    }

    /** A gyakorlat látható-e a felhasználónak (közös vagy saját), és új sablonba tehető-e (nem archivált). */
    private void checkUsable(Exercise exercise, Long userId, Set<Long> alreadyInTemplate) {
        boolean visible = exercise != null
                && (exercise.getOwner() == null || exercise.getOwner().getId().equals(userId));
        if (!visible) {
            throw new NotFoundException("A gyakorlat nem található.");
        }
        if (exercise.isArchived() && !alreadyInTemplate.contains(exercise.getId())) {
            throw new BusinessRuleException("A(z) " + exercise.getName() + " gyakorlat archivált, sablonba nem vehető fel.");
        }
    }

    /** A tervezett értékek közül csak a gyakorlathoz beállított mezők adhatók meg (mind elhagyható). */
    private void validatePlan(Exercise exercise, TemplateExerciseRequest row) {
        String name = exercise.getName();
        rejectIfNotTracked(name, "súly", row.plannedWeightKg() != null, exercise.isTracksWeight());
        rejectIfNotTracked(name, "ismétlésszám", row.plannedReps() != null, exercise.isTracksReps());
        rejectIfNotTracked(name, "időtartam", row.plannedDurationSec() != null, exercise.isTracksDuration());
        rejectIfNotTracked(name, "távolság", row.plannedDistanceM() != null, exercise.isTracksDistance());
    }

    private void rejectIfNotTracked(String exerciseName, String field, boolean present, boolean tracked) {
        if (present && !tracked) {
            throw new BusinessRuleException("A(z) " + exerciseName + " gyakorlathoz nem tervezhető: " + field + ".");
        }
    }

    private TemplateView toView(WorkoutTemplate template) {
        List<TemplateView.ExerciseView> rows = template.getExercises().stream()
                .map(te -> new TemplateView.ExerciseView(
                        te.getExercise().getId(),
                        te.getExercise().getName(),
                        te.getExercise().getCategory(),
                        te.getExercise().isArchived(),
                        te.getPosition(),
                        te.getPlannedSets(),
                        te.getPlannedReps(),
                        te.getPlannedWeightKg(),
                        te.getPlannedDurationSec(),
                        te.getPlannedDistanceM()))
                .toList();
        return new TemplateView(template.getId(), template.getName(), rows);
    }
}

package hu.reptrack.service;

import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.domain.Workout;
import hu.reptrack.domain.WorkoutExercise;
import hu.reptrack.domain.WorkoutSet;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseOption;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.StaleDataException;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutFilter;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.dto.WorkoutSummary;
import hu.reptrack.dto.WorkoutView;
import hu.reptrack.repository.ExerciseOptionRepository;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.repository.WorkoutRepository;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Az edzésnapló használati esetei (UC7, UC8, UC9): edzés rögzítése, módosítása, törlése, másolása és listázása.
 *
 * <p>Minden művelet a bejelentkezett felhasználó azonosítójával dolgozik, és csak a saját adatait éri el.
 * Mentés, módosítás és törlés után a PR-ek újraszámolódnak és a túlterhelési figyelmeztetések egyeztetődnek (UC16).
 */
@Service
@Validated
@Transactional
public class WorkoutService {

    private static final Duration MAX_DURATION = Duration.ofHours(24);
    private static final Set<String> SORTABLE = Set.of("startedAt", "title", "fatigue");

    private final WorkoutRepository workouts;
    private final ExerciseRepository exercises;
    private final UserRepository users;
    private final PersonalRecordService personalRecords;
    private final OverloadService overload;
    private final ExerciseOptionRepository exerciseOptions;

    public WorkoutService(WorkoutRepository workouts, ExerciseRepository exercises, UserRepository users,
                          PersonalRecordService personalRecords, OverloadService overload,
                          ExerciseOptionRepository exerciseOptions) {
        this.workouts = workouts;
        this.exercises = exercises;
        this.users = users;
        this.personalRecords = personalRecords;
        this.overload = overload;
        this.exerciseOptions = exerciseOptions;
    }

    /** Új edzés rögzítése. A gyakorlatok és szettek sorrendjét a listák sorrendje adja. */
    public WorkoutView create(Long userId, @Valid WorkoutRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("A felhasználó nem található."));
        Workout workout = new Workout();
        workout.setUser(user);
        apply(workout, userId, request);
        workouts.saveAndFlush(workout);
        personalRecords.recalculate(userId, exerciseIdsOf(workout));
        overload.evaluate(userId);
        return toView(workout);
    }

    @Transactional(readOnly = true)
    public WorkoutView get(Long userId, Long workoutId) {
        return toView(load(userId, workoutId));
    }

    /** Az edzés módosítása: a gyakorlatok és szettek a kérés szerint lecserélődnek. A {@code version} kötelező. */
    public WorkoutView update(Long userId, Long workoutId, @Valid WorkoutRequest request) {
        Workout workout = load(userId, workoutId);
        if (request.version() == null) {
            throw new BusinessRuleException("Hiányzik az edzés verziószáma a módosításhoz.");
        }
        if (!request.version().equals(workout.getVersion())) {
            throw new StaleDataException("Az edzést közben máshol módosították. Töltsd újra, és mentsd újra a módosításaidat.");
        }
        Set<Long> affected = exerciseIdsOf(workout);       // a módosítás előtti gyakorlatok is érintettek
        apply(workout, userId, request);
        // flush: a visszaadott nézet a módosítás utáni (megnövelt) verziószámot tartalmazza
        workouts.flush();
        affected.addAll(exerciseIdsOf(workout));
        personalRecords.recalculate(userId, affected);
        overload.evaluate(userId);
        return toView(workout);
    }

    public void delete(Long userId, Long workoutId) {
        Workout workout = load(userId, workoutId);
        Set<Long> affected = exerciseIdsOf(workout);
        workouts.delete(workout);
        workouts.flush();
        personalRecords.recalculate(userId, affected);
        overload.evaluate(userId);
    }

    /**
     * Új edzés egy korábbi másolataként: a gyakorlatok és szettek átmásolódnak, az időpont a mostani, az edzés
     * nyitott marad (nincs befejezés és fáradtság). Az azóta archivált gyakorlatok kimaradnak.
     */
    public WorkoutView copy(Long userId, Long sourceWorkoutId) {
        Workout source = load(userId, sourceWorkoutId);
        Workout copy = new Workout();
        copy.setUser(source.getUser());
        copy.setTitle(source.getTitle());
        copy.setStartedAt(Instant.now());
        short position = 1;
        for (WorkoutExercise sourceExercise : source.getExercises()) {
            if (sourceExercise.getExercise().isArchived()) {
                continue;
            }
            WorkoutExercise we = new WorkoutExercise();
            we.setExercise(sourceExercise.getExercise());
            we.setPosition(position++);
            we.setNotes(sourceExercise.getNotes());
            for (WorkoutSet sourceSet : sourceExercise.getSets()) {
                WorkoutSet set = new WorkoutSet();
                set.setPosition(sourceSet.getPosition());
                set.setWeightKg(sourceSet.getWeightKg());
                set.setReps(sourceSet.getReps());
                set.setDurationSec(sourceSet.getDurationSec());
                set.setDistanceM(sourceSet.getDistanceM());
                set.setAvgHeartRate(sourceSet.getAvgHeartRate());
                set.setMaxHeartRate(sourceSet.getMaxHeartRate());
                we.addSet(set);
            }
            copy.addExercise(we);
        }
        return toView(workouts.save(copy));
    }

    /** A felhasználó edzéseinek listája szűrés nélkül, alapértelmezetten a legújabb elöl. */
    @Transactional(readOnly = true)
    public Page<WorkoutSummary> list(Long userId, Pageable pageable) {
        return list(userId, WorkoutFilter.none(), pageable);
    }

    /**
     * Szűrt lista (időszak, gyakorlat, kategória). Rendezni csak {@code startedAt}, {@code title} és
     * {@code fatigue} szerint lehet; rendezés nélkül a legújabb edzés van elöl.
     */
    @Transactional(readOnly = true)
    public Page<WorkoutSummary> list(Long userId, WorkoutFilter filter, Pageable pageable) {
        if (filter.from() != null && filter.to() != null && !filter.from().isBefore(filter.to())) {
            throw new BusinessRuleException("A szűrő kezdő időpontja a záró időpont előtt kell legyen.");
        }
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BusinessRuleException("Erre a mezőre nem lehet rendezni: " + order.getProperty());
            }
        }
        Pageable effective = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "startedAt"))
                : pageable;
        return workouts.search(userId, filter, effective);
    }

    /**
     * Az edzés űrlapján választható gyakorlatok: a felhasználó közös és saját, nem archivált gyakorlatai. Az
     * {@code alsoInclude} azonosítójú gyakorlatok akkor is szerepelnek, ha archiváltak (egy meglévő edzés szerkesztéséhez).
     */
    @Transactional(readOnly = true)
    public List<ExerciseOption> exerciseOptions(Long userId, Collection<Long> alsoInclude) {
        List<ExerciseOption> options = new ArrayList<>(exerciseOptions.findAvailable(userId));
        Set<Long> present = new HashSet<>();
        options.forEach(o -> present.add(o.id()));
        for (Long id : alsoInclude) {
            if (present.add(id)) {
                exercises.findById(id)
                        .filter(e -> e.getOwner() == null || e.getOwner().getId().equals(userId))
                        .ifPresent(e -> options.add(new ExerciseOption(e.getId(), e.getName(), e.getCategory(),
                                e.isTracksWeight(), e.isTracksReps(), e.isTracksDuration(), e.isTracksDistance(),
                                e.isTracksHeartRate(), e.getOwner() == null)));
            }
        }
        return options;
    }

    // ---- belső segédek ----

    private Set<Long> exerciseIdsOf(Workout workout) {
        Set<Long> ids = new HashSet<>();
        for (WorkoutExercise we : workout.getExercises()) {
            ids.add(we.getExercise().getId());
        }
        return ids;
    }

    private Workout load(Long userId, Long workoutId) {
        return workouts.findByIdAndUserId(workoutId, userId)
                .orElseThrow(() -> new NotFoundException("Az edzés nem található."));
    }

    private void apply(Workout workout, Long userId, WorkoutRequest request) {
        validateTimes(request);

        workout.setTitle(request.title().trim());
        workout.setStartedAt(request.startedAt());
        workout.setEndedAt(request.endedAt());
        workout.setFatigue(request.fatigue());
        workout.setNotes(request.notes());

        Set<Long> alreadyInWorkout = new HashSet<>();
        for (WorkoutExercise existing : workout.getExercises()) {
            alreadyInWorkout.add(existing.getExercise().getId());
        }
        List<Long> ids = request.exercises().stream().map(WorkoutExerciseRequest::exerciseId).distinct().toList();
        Map<Long, Exercise> byId = exercises.findAllById(ids).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));

        workout.getExercises().clear();
        short exercisePosition = 1;
        for (WorkoutExerciseRequest exerciseRequest : request.exercises()) {
            Exercise exercise = byId.get(exerciseRequest.exerciseId());
            checkUsable(exercise, userId, alreadyInWorkout);

            WorkoutExercise we = new WorkoutExercise();
            we.setExercise(exercise);
            we.setPosition(exercisePosition++);
            we.setNotes(exerciseRequest.notes());
            short setPosition = 1;
            for (WorkoutSetRequest setRequest : exerciseRequest.sets()) {
                validateSet(exercise, setRequest);
                WorkoutSet set = new WorkoutSet();
                set.setPosition(setPosition++);
                set.setWeightKg(setRequest.weightKg());
                set.setReps(setRequest.reps());
                set.setDurationSec(setRequest.durationSec());
                set.setDistanceM(setRequest.distanceM());
                set.setAvgHeartRate(setRequest.avgHeartRate());
                set.setMaxHeartRate(setRequest.maxHeartRate());
                we.addSet(set);
            }
            workout.addExercise(we);
        }
    }

    private void validateTimes(WorkoutRequest request) {
        Instant start = request.startedAt();
        Instant end = request.endedAt();
        if (end != null) {
            if (!end.isAfter(start)) {
                throw new BusinessRuleException("Az edzés befejezése a kezdése után kell legyen.");
            }
            if (Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
                throw new BusinessRuleException("Az edzés nem tarthat 24 óránál tovább.");
            }
            if (request.fatigue() == null) {
                throw new BusinessRuleException("Lezárt edzéshez meg kell adni a fáradtságértéket (1–10).");
            }
        }
    }

    /** A gyakorlat látható-e a felhasználónak (közös vagy saját), és új edzésbe tehető-e (nem archivált). */
    private void checkUsable(Exercise exercise, Long userId, Set<Long> alreadyInWorkout) {
        boolean visible = exercise != null
                && (exercise.getOwner() == null || exercise.getOwner().getId().equals(userId));
        if (!visible) {
            throw new NotFoundException("A gyakorlat nem található.");
        }
        if (exercise.isArchived() && !alreadyInWorkout.contains(exercise.getId())) {
            throw new BusinessRuleException("A(z) " + exercise.getName() + " gyakorlat archivált, új edzésbe nem vehető fel.");
        }
    }

    /** A szett csak a gyakorlathoz beállított mezőket tartalmazhatja, a kötelezők pedig nem hiányozhatnak. */
    private void validateSet(Exercise exercise, WorkoutSetRequest set) {
        String name = exercise.getName();
        rejectIfNotTracked(name, "súly", set.weightKg() != null, exercise.isTracksWeight());
        rejectIfNotTracked(name, "ismétlésszám", set.reps() != null, exercise.isTracksReps());
        rejectIfNotTracked(name, "időtartam", set.durationSec() != null, exercise.isTracksDuration());
        rejectIfNotTracked(name, "távolság", set.distanceM() != null, exercise.isTracksDistance());
        rejectIfNotTracked(name, "pulzus",
                set.avgHeartRate() != null || set.maxHeartRate() != null, exercise.isTracksHeartRate());

        if (exercise.isTracksReps() && set.reps() == null) {
            throw new BusinessRuleException("A(z) " + name + " gyakorlatnál az ismétlésszám kötelező.");
        }
        // saját testsúlyos gyakorlatnál a súly (külső terhelés) elhagyható
        if (exercise.isTracksWeight() && exercise.getCategory() == ExerciseCategory.WEIGHTS && set.weightKg() == null) {
            throw new BusinessRuleException("A(z) " + name + " gyakorlatnál a súly kötelező.");
        }
        if (exercise.isTracksDuration() && set.durationSec() == null) {
            throw new BusinessRuleException("A(z) " + name + " gyakorlatnál az időtartam kötelező.");
        }
        if (set.avgHeartRate() != null && set.maxHeartRate() != null && set.maxHeartRate() < set.avgHeartRate()) {
            throw new BusinessRuleException("A maximális pulzus nem lehet kisebb az átlagosnál.");
        }
    }

    private void rejectIfNotTracked(String exerciseName, String field, boolean present, boolean tracked) {
        if (present && !tracked) {
            throw new BusinessRuleException("A(z) " + exerciseName + " gyakorlathoz nem rögzíthető: " + field + ".");
        }
    }

    private WorkoutView toView(Workout workout) {
        List<WorkoutView.ExerciseView> exerciseViews = workout.getExercises().stream()
                .map(we -> new WorkoutView.ExerciseView(
                        we.getId(),
                        we.getExercise().getId(),
                        we.getExercise().getName(),
                        we.getExercise().getCategory(),
                        we.getPosition(),
                        we.getNotes(),
                        we.getSets().stream()
                                .map(s -> new WorkoutView.SetView(s.getId(), s.getPosition(), s.getWeightKg(), s.getReps(),
                                        s.getDurationSec(), s.getDistanceM(), s.getAvgHeartRate(), s.getMaxHeartRate()))
                                .toList()))
                .toList();
        return new WorkoutView(workout.getId(), workout.getTitle(), workout.getStartedAt(), workout.getEndedAt(),
                workout.getFatigue(), workout.getNotes(), workout.getVersion(), exerciseViews);
    }
}

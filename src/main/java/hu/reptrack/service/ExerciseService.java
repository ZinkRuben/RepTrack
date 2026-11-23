package hu.reptrack.service;

import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.DuplicateExerciseNameException;
import hu.reptrack.dto.EditableExercise;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseFilter;
import hu.reptrack.dto.ExerciseRequest;
import hu.reptrack.dto.ExerciseSource;
import hu.reptrack.dto.ExerciseView;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.StaleDataException;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import jakarta.validation.Valid;
import java.text.Normalizer;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * A gyakorlat-adatbázis (UC4, UC5, UC6): böngészés, saját gyakorlatok kezelése, a közös gyakorlatok karbantartása.
 *
 * <ul>
 *   <li>Mindenki a közös és a saját (privát) gyakorlatait látja; más felhasználó privát gyakorlatát senki, az
 *       adminisztrátor sem (a művelet ilyenkor "nem található" hibát ad, hogy a létezése se derüljön ki).</li>
 *   <li>A saját gyakorlatot csak a tulajdonosa módosíthatja, a közöset csak adminisztrátor (metódusszintű
 *       jogosultság is védi).</li>
 *   <li>A használatban lévő gyakorlat (edzésben, sablonban vagy célban szerepel) nem törölhető, csak archiválható,
 *       és a kategóriája és a rögzíthető mezői sem módosíthatók, hogy a korábbi szettek érvényesek maradjanak.</li>
 *   <li>Az archivált gyakorlat új edzésbe nem vehető fel, a korábbiakban megmarad; visszaállítható.</li>
 * </ul>
 */
@Service
@Validated
@Transactional
public class ExerciseService {

    private static final String NOT_FOUND = "A gyakorlat nem található.";

    private final ExerciseRepository exercises;
    private final UserRepository users;

    public ExerciseService(ExerciseRepository exercises, UserRepository users) {
        this.exercises = exercises;
        this.users = users;
    }

    // ---- böngészés ----

    @Transactional(readOnly = true)
    public List<ExerciseView> search(Long userId, ExerciseFilter filter) {
        boolean archived = filter.showArchived();
        List<Exercise> found = exercises.searchVisible(userId, namePattern(filter.name()), filter.category(),
                filter.muscle(), filter.source() == ExerciseSource.SHARED, filter.source() == ExerciseSource.OWN,
                archived, archived && isAdmin(userId));
        Set<Long> inUse = inUse(found.stream().map(Exercise::getId).toList());
        return found.stream().map(e -> toView(e, inUse.contains(e.getId()))).toList();
    }

    // ---- saját gyakorlatok (UC5) ----

    @Transactional(readOnly = true)
    public EditableExercise getOwnForEdit(Long userId, Long exerciseId) {
        return editable(loadOwn(userId, exerciseId));
    }

    /** @return az új gyakorlat azonosítója */
    public Long createOwn(Long userId, @Valid ExerciseRequest request) {
        User owner = users.findById(userId).orElseThrow(() -> new NotFoundException("A felhasználó nem található."));
        if (exercises.existsByOwnerIdAndNameIgnoreCase(userId, request.getName().strip())) {
            throw new DuplicateExerciseNameException();
        }
        return create(owner, request);
    }

    public void updateOwn(Long userId, Long exerciseId, @Valid ExerciseRequest request) {
        Exercise exercise = loadOwn(userId, exerciseId);
        if (exercises.existsByOwnerIdAndNameIgnoreCaseAndIdNot(userId, request.getName().strip(), exerciseId)) {
            throw new DuplicateExerciseNameException();
        }
        update(exercise, request);
    }

    public void setOwnArchived(Long userId, Long exerciseId, boolean archived) {
        loadOwn(userId, exerciseId).setArchived(archived);
    }

    public void deleteOwn(Long userId, Long exerciseId) {
        delete(loadOwn(userId, exerciseId));
    }

    // ---- közös gyakorlatok (UC6, csak adminisztrátor) ----

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public EditableExercise getSharedForEdit(Long exerciseId) {
        return editable(loadShared(exerciseId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Long createShared(@Valid ExerciseRequest request) {
        if (exercises.existsByOwnerIsNullAndNameIgnoreCase(request.getName().strip())) {
            throw new DuplicateExerciseNameException();
        }
        return create(null, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void updateShared(Long exerciseId, @Valid ExerciseRequest request) {
        Exercise exercise = loadShared(exerciseId);
        if (exercises.existsByOwnerIsNullAndNameIgnoreCaseAndIdNot(request.getName().strip(), exerciseId)) {
            throw new DuplicateExerciseNameException();
        }
        update(exercise, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void setSharedArchived(Long exerciseId, boolean archived) {
        loadShared(exerciseId).setArchived(archived);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteShared(Long exerciseId) {
        delete(loadShared(exerciseId));
    }

    // ---- közös logika ----

    private Exercise loadOwn(Long userId, Long exerciseId) {
        return exercises.findById(exerciseId)
                .filter(e -> e.getOwner() != null && e.getOwner().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    private Exercise loadShared(Long exerciseId) {
        return exercises.findById(exerciseId)
                .filter(Exercise::isShared)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    private Long create(User owner, ExerciseRequest request) {
        Exercise exercise = new Exercise();
        exercise.setOwner(owner);
        apply(exercise, request, true);
        if (owner == null) {
            // közös gyakorlat: a képe a nevéből képzett kulccsal tehető a static/images/exercises mappába
            exercise.setImageKey(imageKeyFor(exercise.getName()));
        }
        try {
            return exercises.saveAndFlush(exercise).getId();
        } catch (DataIntegrityViolationException e) {
            // két egyidejű mentés ugyanazzal a névvel: a második az egyedi indexen akad el
            throw new DuplicateExerciseNameException();
        }
    }

    /**
     * A gyakorlat képkulcsa a nevéből: ékezet nélkül, kisbetűvel, a szavak kötőjellel (Ferde pados fekvenyomás →
     * ferde-pados-fekvenyomas). Ugyanez a szabály adta a beépített gyakorlatok kulcsát (V7 migráció). A kulcs a
     * létrehozáskor rögzül, a későbbi átnevezés nem változtatja meg.
     */
    static String imageKeyFor(String name) {
        String plain = Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        String key = plain.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (key.length() > 60) {
            key = key.substring(0, 60).replaceAll("-+$", "");
        }
        return key.isEmpty() ? null : key;
    }

    private void update(Exercise exercise, ExerciseRequest request) {
        if (request.getVersion() == null || !request.getVersion().equals(exercise.getVersion())) {
            throw new StaleDataException("A gyakorlatot közben máshol módosították. Töltsd be újra, és ismételd meg a módosítást.");
        }
        boolean locked = isInUse(exercise.getId());
        apply(exercise, request, !locked);
        try {
            exercises.flush();
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateExerciseNameException();
        }
    }

    /**
     * A kérés adatainak átvétele. A szerkezeti adatok (kategória, kardió fajtája, rögzíthető mezők) csak akkor
     * változnak, ha a gyakorlat még nincs használatban.
     */
    private static void apply(Exercise exercise, ExerciseRequest request, boolean includeStructure) {
        exercise.setName(request.getName().strip());
        exercise.setDescription(request.getDescription() == null || request.getDescription().isBlank()
                ? null : request.getDescription().strip());
        exercise.setPrimaryMuscle(request.getPrimaryMuscle());
        Set<MuscleGroup> secondary = new LinkedHashSet<>(request.getSecondaryMuscles());
        secondary.remove(request.getPrimaryMuscle());
        exercise.getSecondaryMuscles().clear();
        exercise.getSecondaryMuscles().addAll(secondary);

        if (includeStructure) {
            exercise.setCategory(request.getCategory());
            exercise.setCardioType(request.getCategory() == ExerciseCategory.CARDIO ? request.getCardioType() : null);
            exercise.setTracksWeight(request.isTracksWeight());
            exercise.setTracksReps(request.isTracksReps());
            exercise.setTracksDuration(request.isTracksDuration());
            exercise.setTracksDistance(request.isTracksDistance());
            exercise.setTracksHeartRate(request.isTracksHeartRate());
        }
    }

    private void delete(Exercise exercise) {
        if (isInUse(exercise.getId())) {
            throw new BusinessRuleException("A(z) " + exercise.getName() + " gyakorlat már szerepel edzésben, sablonban "
                    + "vagy célban, ezért nem törölhető, csak archiválható.");
        }
        exercises.delete(exercise);
        exercises.flush();
    }

    private boolean isInUse(Long exerciseId) {
        return !inUse(List.of(exerciseId)).isEmpty();
    }

    /** A megadottak közül azok, amelyek edzésben, sablonban vagy célban szerepelnek. */
    private Set<Long> inUse(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        Set<Long> used = new HashSet<>(exercises.findIdsUsedInWorkouts(ids));
        used.addAll(exercises.findIdsUsedInTemplates(ids));
        used.addAll(exercises.findIdsUsedInGoals(ids));
        return used;
    }

    private boolean isAdmin(Long userId) {
        return users.findById(userId).map(u -> u.getRole() == Role.ADMIN).orElse(false);
    }

    private EditableExercise editable(Exercise e) {
        ExerciseRequest form = new ExerciseRequest();
        form.setName(e.getName());
        form.setCategory(e.getCategory());
        form.setCardioType(e.getCardioType());
        form.setPrimaryMuscle(e.getPrimaryMuscle());
        form.setSecondaryMuscles(new LinkedHashSet<>(e.getSecondaryMuscles()));
        form.setDescription(e.getDescription());
        form.setTracksWeight(e.isTracksWeight());
        form.setTracksReps(e.isTracksReps());
        form.setTracksDuration(e.isTracksDuration());
        form.setTracksDistance(e.isTracksDistance());
        form.setTracksHeartRate(e.isTracksHeartRate());
        form.setVersion(e.getVersion());
        return new EditableExercise(e.getId(), e.isShared(), e.isArchived(), isInUse(e.getId()), form);
    }

    /** Részszöveg-keresés mintája; az üres név nem szűr, a LIKE helyettesítő karakterei szó szerint értendők. */
    private static String namePattern(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String escaped = name.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static ExerciseView toView(Exercise e, boolean inUse) {
        return new ExerciseView(
                e.getId(), e.getName(), e.getCategory(), e.getPrimaryMuscle(),
                e.getSecondaryMuscles().stream().sorted().toList(),
                e.getCardioType(), e.getDescription(),
                e.isTracksWeight(), e.isTracksReps(), e.isTracksDuration(), e.isTracksDistance(), e.isTracksHeartRate(),
                e.isShared(), e.isArchived(), inUse);
    }
}

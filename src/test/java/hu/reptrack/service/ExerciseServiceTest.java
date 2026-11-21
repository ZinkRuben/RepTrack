package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.domain.Workout;
import hu.reptrack.domain.WorkoutExercise;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.DuplicateExerciseNameException;
import hu.reptrack.dto.EditableExercise;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseFilter;
import hu.reptrack.dto.ExerciseOption;
import hu.reptrack.dto.ExerciseRequest;
import hu.reptrack.dto.ExerciseSource;
import hu.reptrack.dto.ExerciseView;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.StaleDataException;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.repository.WorkoutRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/** A gyakorlat-adatbázis (UC4, UC5, UC6) a V2 seed adatain: böngészés, saját gyakorlatok, közös gyakorlatok. */
@Transactional
class ExerciseServiceTest extends IntegrationTestBase {

    @Autowired ExerciseService service;
    @Autowired WorkoutService workoutService;
    @Autowired ExerciseRepository exercises;
    @Autowired UserRepository users;
    @Autowired WorkoutRepository workouts;

    User anna;
    User bela;
    User admin;

    @BeforeEach
    void setUp() {
        anna = user("anna@example.com", Role.USER);
        bela = user("bela@example.com", Role.USER);
        admin = user("admin@example.com", Role.ADMIN);
    }

    // ---- segédek ----

    private User user(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setDisplayName("Teszt");
        u.setRole(role);
        return users.save(u);
    }

    private static ExerciseRequest weights(String name) {
        ExerciseRequest r = new ExerciseRequest();
        r.setName(name);
        r.setCategory(ExerciseCategory.WEIGHTS);
        r.setPrimaryMuscle(MuscleGroup.CHEST);
        r.setTracksWeight(true);
        r.setTracksReps(true);
        return r;
    }

    private static ExerciseFilter filter(String name, ExerciseCategory category, MuscleGroup muscle) {
        return new ExerciseFilter(name, category, muscle, null, null);
    }

    private List<String> names(User user, ExerciseFilter filter) {
        return service.search(user.getId(), filter).stream().map(ExerciseView::name).toList();
    }

    private Long sharedId(String name) {
        return exercises.findAll().stream().filter(e -> e.isShared() && e.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }

    /** A gyakorlat bekerül egy edzésbe (ettől kezdve használatban van). */
    private void useInWorkout(User user, Long exerciseId) {
        Workout w = new Workout();
        w.setUser(user);
        w.setTitle("Edzés");
        w.setStartedAt(Instant.now());
        WorkoutExercise we = new WorkoutExercise();
        we.setExercise(exercises.findById(exerciseId).orElseThrow());
        we.setPosition((short) 1);
        w.addExercise(we);
        workouts.saveAndFlush(w);
    }

    private String[] violatedProperties(ExerciseRequest request) {
        try {
            service.createOwn(anna.getId(), request);
        } catch (ConstraintViolationException e) {
            return e.getConstraintViolations().stream()
                    .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                    .map(path -> path.substring(path.lastIndexOf('.') + 1))
                    .sorted().toArray(String[]::new);
        }
        throw new AssertionError("ConstraintViolationException-t vártunk");
    }

    // ---- böngészés (UC4) ----

    @Test
    void everyoneSeesTheSharedCatalogPlusOnlyTheirOwnExercises() {
        service.createOwn(anna.getId(), weights("Anna nyomása"));

        assertThat(names(anna, ExerciseFilter.none())).hasSize(51).contains("Anna nyomása", "Guggolás");
        assertThat(names(bela, ExerciseFilter.none())).hasSize(50).doesNotContain("Anna nyomása");
        assertThat(names(admin, ExerciseFilter.none())).as("az admin sem látja más privát gyakorlatát")
                .hasSize(50).doesNotContain("Anna nyomása");
    }

    @Test
    void blankNameDoesNotFilter() {
        assertThat(service.search(anna.getId(), filter("   ", null, null))).hasSize(50);
    }

    @Test
    void nameSearchIsCaseInsensitiveSubstring() {
        assertThat(names(anna, filter("GUGGOL", null, null))).containsExactlyInAnyOrder(
                "Guggolás", "Bolgár guggolás", "Pisztolyguggolás", "Saját testsúlyos guggolás");
    }

    @Test
    void likeWildcardsAreMatchedLiterally() {
        assertThat(service.search(anna.getId(), filter("%", null, null))).isEmpty();
        assertThat(service.search(anna.getId(), filter("_", null, null))).isEmpty();
    }

    @Test
    void filtersByCategory() {
        List<ExerciseView> cardio = service.search(anna.getId(), filter(null, ExerciseCategory.CARDIO, null));
        assertThat(cardio).hasSize(10).allMatch(e -> e.category() == ExerciseCategory.CARDIO);
    }

    @Test
    void muscleFilterMatchesPrimaryAndSecondaryMuscles() {
        assertThat(names(anna, filter(null, null, MuscleGroup.TRICEPS)))
                .contains("Francia nyomás", "Tolódzkodás")   // elsődleges
                .contains("Fekvenyomás", "Fekvőtámasz")       // másodlagos
                .doesNotContain("Guggolás");
    }

    @Test
    void filtersCombine() {
        assertThat(names(anna, filter("támasz", ExerciseCategory.BODYWEIGHT, null)))
                .containsExactlyInAnyOrder("Fekvőtámasz", "Alkartámasz", "Négyütemű fekvőtámasz felugrással");
        assertThat(names(anna, filter("támasz", ExerciseCategory.BODYWEIGHT, MuscleGroup.CHEST)))
                .containsExactlyInAnyOrder("Fekvőtámasz", "Négyütemű fekvőtámasz felugrással");
    }

    @Test
    void sourceFilterSeparatesSharedAndOwnExercises() {
        service.createOwn(anna.getId(), weights("Anna nyomása"));

        assertThat(names(anna, new ExerciseFilter(null, null, null, ExerciseSource.OWN, null)))
                .containsExactly("Anna nyomása");
        assertThat(names(anna, new ExerciseFilter(null, null, null, ExerciseSource.SHARED, null)))
                .hasSize(50).doesNotContain("Anna nyomása");
    }

    @Test
    void viewCarriesMusclesAndFlags() {
        ExerciseView bench = service.search(anna.getId(), filter("Fekvenyomás", null, null)).stream()
                .filter(e -> e.name().equals("Fekvenyomás")).findFirst().orElseThrow();
        assertThat(bench.primaryMuscle()).isEqualTo(MuscleGroup.CHEST);
        assertThat(bench.secondaryMuscles()).containsExactly(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS);
        assertThat(bench.shared()).isTrue();
        assertThat(bench.archived()).isFalse();
        assertThat(bench.inUse()).isFalse();

        useInWorkout(anna, bench.id());
        assertThat(service.search(anna.getId(), filter("Fekvenyomás", null, null)).stream()
                .filter(e -> e.name().equals("Fekvenyomás")).findFirst().orElseThrow().inUse()).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void archivedExercisesAppearOnlyWithTheArchivedFilter() {
        Long own = service.createOwn(anna.getId(), weights("Anna nyomása"));
        service.setOwnArchived(anna.getId(), own, true);
        service.setSharedArchived(sharedId("Gyaloglás"), true);
        ExerciseFilter archived = new ExerciseFilter(null, null, null, null, true);

        assertThat(names(anna, ExerciseFilter.none())).hasSize(49).doesNotContain("Anna nyomása", "Gyaloglás");
        assertThat(names(anna, archived)).as("a felhasználó csak a saját archiváltjait látja")
                .containsExactly("Anna nyomása");
        assertThat(names(admin, archived)).as("az admin a közös archiváltakat is").containsExactly("Gyaloglás");
    }

    // ---- saját gyakorlat (UC5) ----

    @Test
    void createsPrivateExerciseAndNormalizesTheInput() {
        ExerciseRequest r = weights("  Kábeles mellzárás ");
        r.setSecondaryMuscles(Set.of(MuscleGroup.CHEST, MuscleGroup.SHOULDERS)); // az elsődleges is be van jelölve
        r.setCardioType(CardioType.RUNNING);                                     // nem kardió: figyelmen kívül marad
        r.setDescription("   ");

        Exercise saved = exercises.findById(service.createOwn(anna.getId(), r)).orElseThrow();
        assertThat(saved.getOwner().getId()).isEqualTo(anna.getId());
        assertThat(saved.getName()).isEqualTo("Kábeles mellzárás");
        assertThat(saved.getSecondaryMuscles()).containsExactly(MuscleGroup.SHOULDERS);
        assertThat(saved.getCardioType()).isNull();
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.isArchived()).isFalse();
    }

    @Test
    void trackingRulesFollowTheCategory() {
        ExerciseRequest noReps = weights("Súly ismétlés nélkül");
        noReps.setTracksReps(false);
        assertThat(violatedProperties(noReps)).containsExactly("weightsTrackingValid");

        ExerciseRequest nothing = weights("Semmi");
        nothing.setCategory(ExerciseCategory.BODYWEIGHT);
        nothing.setTracksWeight(false);
        nothing.setTracksReps(false);
        assertThat(violatedProperties(nothing)).containsExactly("anythingTracked", "bodyweightTrackingValid");

        ExerciseRequest run = weights("Futás pályán");
        run.setCategory(ExerciseCategory.CARDIO);
        run.setTracksWeight(false);
        run.setTracksReps(false);
        run.setTracksDuration(true);
        assertThat(violatedProperties(run)).as("kardiónál meg kell adni a mérés módját").containsExactly("cardioTypeGiven");
        run.setCardioType(CardioType.RUNNING);
        assertThat(violatedProperties(run)).as("tempóhoz táv is kell").containsExactly("cardioTrackingValid");

        ExerciseRequest blank = weights(" ");
        blank.setPrimaryMuscle(null);
        assertThat(violatedProperties(blank)).containsExactly("name", "primaryMuscle");
    }

    @Test
    void ownNamesAreUniquePerUserOnly() {
        service.createOwn(anna.getId(), weights("Saját húzás"));

        assertThatThrownBy(() -> service.createOwn(anna.getId(), weights("SAJÁT HÚZÁS")))
                .isInstanceOf(DuplicateExerciseNameException.class);
        assertThat(service.createOwn(bela.getId(), weights("Saját húzás"))).isNotNull();
        assertThat(service.createOwn(anna.getId(), weights("Guggolás")))
                .as("a közös gyakorlat nevét a saját is viselheti").isNotNull();
    }

    @Test
    void onlyTheOwnerCanSeeOrChangeAPrivateExercise() {
        Long own = service.createOwn(anna.getId(), weights("Anna nyomása"));
        Long shared = sharedId("Guggolás");
        EditableExercise form = service.getOwnForEdit(anna.getId(), own);

        assertThatThrownBy(() -> service.getOwnForEdit(bela.getId(), own)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.updateOwn(bela.getId(), own, form.form())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.setOwnArchived(bela.getId(), own, true)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteOwn(bela.getId(), own)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteOwn(anna.getId(), shared))
                .as("közös gyakorlat a saját műveletekkel nem érhető el").isInstanceOf(NotFoundException.class);
        assertThat(exercises.findById(own)).isPresent();
    }

    @Test
    void updateNeedsTheCurrentVersion() {
        Long own = service.createOwn(anna.getId(), weights("Anna nyomása"));
        ExerciseRequest first = service.getOwnForEdit(anna.getId(), own).form();
        ExerciseRequest second = service.getOwnForEdit(anna.getId(), own).form();

        first.setName("Anna nyomása 2");
        service.updateOwn(anna.getId(), own, first);
        exercises.flush();

        second.setName("Ütköző név");
        assertThatThrownBy(() -> service.updateOwn(anna.getId(), own, second)).isInstanceOf(StaleDataException.class);
        assertThat(exercises.findById(own).orElseThrow().getName()).isEqualTo("Anna nyomása 2");
    }

    @Test
    void exerciseInUseKeepsItsCategoryAndTrackedFields() {
        Long own = service.createOwn(anna.getId(), weights("Anna nyomása"));
        useInWorkout(anna, own);
        EditableExercise editing = service.getOwnForEdit(anna.getId(), own);
        assertThat(editing.inUse()).isTrue();

        ExerciseRequest r = editing.form();
        r.setName("Anna ferde nyomása");
        r.setDescription("Ferde padon.");
        r.setCategory(ExerciseCategory.BODYWEIGHT);
        r.setTracksWeight(false);
        service.updateOwn(anna.getId(), own, r);

        Exercise saved = exercises.findById(own).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Anna ferde nyomása");
        assertThat(saved.getDescription()).isEqualTo("Ferde padon.");
        assertThat(saved.getCategory()).isEqualTo(ExerciseCategory.WEIGHTS);
        assertThat(saved.isTracksWeight()).isTrue();
    }

    @Test
    void exerciseInUseCannotBeDeletedButCanBeArchivedAndRestored() {
        Long own = service.createOwn(anna.getId(), weights("Anna nyomása"));
        useInWorkout(anna, own);

        assertThatThrownBy(() -> service.deleteOwn(anna.getId(), own))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nem törölhető, csak archiválható");

        service.setOwnArchived(anna.getId(), own, true);
        assertThat(workoutService.exerciseOptions(anna.getId(), List.of())).extracting(ExerciseOption::id)
                .as("archivált gyakorlat új edzésbe nem választható").doesNotContain(own);

        service.setOwnArchived(anna.getId(), own, false);
        assertThat(workoutService.exerciseOptions(anna.getId(), List.of())).extracting(ExerciseOption::id).contains(own);
    }

    @Test
    void unusedExerciseCanBeDeleted() {
        Long own = service.createOwn(anna.getId(), weights("Anna nyomása"));
        service.deleteOwn(anna.getId(), own);
        assertThat(exercises.findById(own)).isEmpty();
    }

    // ---- közös gyakorlatok (UC6) ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminMaintainsSharedExercisesThatEveryoneSees() {
        Long id = service.createShared(weights("Kábeles mellzárás"));
        assertThat(exercises.findById(id).orElseThrow().isShared()).isTrue();
        assertThat(names(anna, ExerciseFilter.none())).contains("Kábeles mellzárás");
        assertThat(names(bela, ExerciseFilter.none())).contains("Kábeles mellzárás");

        ExerciseRequest r = service.getSharedForEdit(id).form();
        r.setName("Kábeles mellzárás állva");
        service.updateShared(id, r);
        assertThat(exercises.findById(id).orElseThrow().getName()).isEqualTo("Kábeles mellzárás állva");

        assertThatThrownBy(() -> service.createShared(weights("guggolás")))
                .isInstanceOf(DuplicateExerciseNameException.class);

        service.deleteShared(id);
        assertThat(exercises.findById(id)).isEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sharedExerciseInUseCannotBeDeleted() {
        Long squat = sharedId("Guggolás");
        useInWorkout(anna, squat);

        assertThatThrownBy(() -> service.deleteShared(squat)).isInstanceOf(BusinessRuleException.class);
        service.setSharedArchived(squat, true);
        assertThat(exercises.findById(squat).orElseThrow().isArchived()).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCannotReachPrivateExercisesThroughSharedOperations() {
        Long own = service.createOwn(anna.getId(), weights("Anna nyomása"));

        assertThatThrownBy(() -> service.getSharedForEdit(own)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.setSharedArchived(own, true)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.deleteShared(own)).isInstanceOf(NotFoundException.class);
    }

    @Test
    @WithMockUser(roles = "USER")
    void sharedOperationsRequireTheAdminRole() {
        Long squat = sharedId("Guggolás");

        assertThatThrownBy(() -> service.createShared(weights("Új közös"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getSharedForEdit(squat)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.updateShared(squat, weights("Guggolás"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.setSharedArchived(squat, true)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deleteShared(squat)).isInstanceOf(AccessDeniedException.class);
        assertThat(exercises.findById(squat).orElseThrow().isArchived()).isFalse();
    }
}

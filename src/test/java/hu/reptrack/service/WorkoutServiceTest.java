package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.StaleDataException;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.dto.WorkoutSummary;
import hu.reptrack.dto.WorkoutView;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.repository.WorkoutRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class WorkoutServiceTest extends IntegrationTestBase {

    @Autowired WorkoutService service;
    @Autowired WorkoutRepository workouts;
    @Autowired ExerciseRepository exercises;
    @Autowired UserRepository users;
    @Autowired EntityManager em;

    User alice;
    User bob;
    Exercise squat;    // súlyzós: súly + ismétlés
    Exercise run;      // kardió: időtartam + távolság + pulzus
    Exercise pullUp;   // saját testsúlyos: ismétlés, a külső súly elhagyható

    @BeforeEach
    void setUp() {
        alice = users.save(user("alice@example.com"));
        bob = users.save(user("bob@example.com"));
        squat = exercise("Guggolás", ExerciseCategory.WEIGHTS, true, true, false, false, false, null);
        run = exercise("Futás", ExerciseCategory.CARDIO, false, false, true, true, true, null);
        pullUp = exercise("Húzódzkodás", ExerciseCategory.BODYWEIGHT, true, true, false, false, false, null);
    }

    // ---- segédek ----

    private User user(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setDisplayName("Teszt");
        return u;
    }

    private Exercise exercise(String name, ExerciseCategory category, boolean weight, boolean reps, boolean duration,
                              boolean distance, boolean heartRate, User owner) {
        Exercise e = new Exercise();
        e.setName(name);
        e.setCategory(category);
        e.setTracksWeight(weight);
        e.setTracksReps(reps);
        e.setTracksDuration(duration);
        e.setTracksDistance(distance);
        e.setTracksHeartRate(heartRate);
        e.setOwner(owner);
        return exercises.save(e);
    }

    private WorkoutSetRequest strengthSet(String kg, int reps) {
        return new WorkoutSetRequest(new BigDecimal(kg), (short) reps, null, null, null, null);
    }

    private WorkoutRequest request(Long version, WorkoutExerciseRequest... exerciseRequests) {
        Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
        return new WorkoutRequest("Edzés", start, start.plus(50, ChronoUnit.MINUTES), (short) 7, null, version,
                List.of(exerciseRequests));
    }

    private WorkoutExerciseRequest squatBlock(WorkoutSetRequest... sets) {
        return new WorkoutExerciseRequest(squat.getId(), null, List.of(sets));
    }

    // ---- létrehozás, lekérdezés ----

    @Test
    void createsWorkoutAndAssignsPositionsByListOrder() {
        WorkoutView view = service.create(alice.getId(), request(null,
                squatBlock(strengthSet("100", 5), strengthSet("105", 5)),
                new WorkoutExerciseRequest(run.getId(), "könnyű kör",
                        List.of(new WorkoutSetRequest(null, null, 1800, 5000, (short) 140, (short) 165)))));
        em.flush();
        em.clear();

        WorkoutView loaded = service.get(alice.getId(), view.id());
        assertThat(loaded.exercises()).extracting(WorkoutView.ExerciseView::exerciseName)
                .containsExactly("Guggolás", "Futás");
        assertThat(loaded.exercises()).extracting(WorkoutView.ExerciseView::position)
                .containsExactly((short) 1, (short) 2);
        assertThat(loaded.exercises().get(0).sets()).extracting(WorkoutView.SetView::position)
                .containsExactly((short) 1, (short) 2);
        assertThat(loaded.exercises().get(0).sets().get(1).weightKg()).isEqualByComparingTo("105");
        assertThat(loaded.exercises().get(1).notes()).isEqualTo("könnyű kör");
    }

    @Test
    void canCreateEmptyWorkout() {
        WorkoutView view = service.create(alice.getId(), request(null));
        assertThat(view.exercises()).isEmpty();
    }

    @Test
    void otherUserCannotTouchWorkout() {
        Long id = service.create(alice.getId(), request(null, squatBlock(strengthSet("100", 5)))).id();

        assertThatThrownBy(() -> service.get(bob.getId(), id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(bob.getId(), id, request(0L))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(bob.getId(), id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.copy(bob.getId(), id)).isInstanceOf(NotFoundException.class);
    }

    // ---- szabályok ----

    @Test
    void rejectsFieldsTheExerciseDoesNotTrack() {
        var distanceOnSquat = new WorkoutSetRequest(new BigDecimal("100"), (short) 5, null, 1000, null, null);
        assertThatThrownBy(() -> service.create(alice.getId(), request(null, squatBlock(distanceOnSquat))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("távolság");

        var weightOnRun = new WorkoutSetRequest(new BigDecimal("10"), null, 600, null, null, null);
        assertThatThrownBy(() -> service.create(alice.getId(), request(null,
                new WorkoutExerciseRequest(run.getId(), null, List.of(weightOnRun)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("súly");
    }

    @Test
    void requiresMandatoryFields() {
        var noReps = new WorkoutSetRequest(new BigDecimal("100"), null, null, null, null, null);
        assertThatThrownBy(() -> service.create(alice.getId(), request(null, squatBlock(noReps))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ismétlésszám");

        var noWeight = new WorkoutSetRequest(null, (short) 5, null, null, null, null);
        assertThatThrownBy(() -> service.create(alice.getId(), request(null, squatBlock(noWeight))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("súly");

        var noDuration = new WorkoutSetRequest(null, null, null, 5000, null, null);
        assertThatThrownBy(() -> service.create(alice.getId(), request(null,
                new WorkoutExerciseRequest(run.getId(), null, List.of(noDuration)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("időtartam");
    }

    @Test
    void externalWeightIsOptionalForBodyweightExercise() {
        var plain = new WorkoutSetRequest(null, (short) 8, null, null, null, null);
        var weighted = new WorkoutSetRequest(new BigDecimal("10"), (short) 5, null, null, null, null);
        WorkoutView view = service.create(alice.getId(), request(null,
                new WorkoutExerciseRequest(pullUp.getId(), null, List.of(plain, weighted))));
        assertThat(view.exercises().get(0).sets()).hasSize(2);
    }

    @Test
    void rejectsMaxHeartRateBelowAverage() {
        var set = new WorkoutSetRequest(null, null, 600, null, (short) 150, (short) 140);
        assertThatThrownBy(() -> service.create(alice.getId(), request(null,
                new WorkoutExerciseRequest(run.getId(), null, List.of(set)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("pulzus");
    }

    @Test
    void validatesTimesAndFatigue() {
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS);

        assertThatThrownBy(() -> service.create(alice.getId(),
                new WorkoutRequest("E", start, start.minusSeconds(60), (short) 5, null, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("után");

        assertThatThrownBy(() -> service.create(alice.getId(),
                new WorkoutRequest("E", start.minus(30, ChronoUnit.HOURS), start, (short) 5, null, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("24 órá");

        assertThatThrownBy(() -> service.create(alice.getId(),
                new WorkoutRequest("E", start, start.plusSeconds(3600), null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("fáradtság");

        // nyitott edzéshez (nincs befejezés) nem kell fáradtság
        assertThat(service.create(alice.getId(), new WorkoutRequest("E", start, null, null, null, null, null)).id())
                .isNotNull();
    }

    @Test
    void beanValidationRejectsOutOfRangeValues() {
        var badReps = new WorkoutSetRequest(new BigDecimal("100"), (short) 0, null, null, null, null);
        assertThatThrownBy(() -> service.create(alice.getId(), request(null, squatBlock(badReps))))
                .isInstanceOf(ConstraintViolationException.class);

        var tooHeavy = new WorkoutSetRequest(new BigDecimal("501"), (short) 5, null, null, null, null);
        assertThatThrownBy(() -> service.create(alice.getId(), request(null, squatBlock(tooHeavy))))
                .isInstanceOf(ConstraintViolationException.class);

        Instant start = Instant.now().minusSeconds(60);
        assertThatThrownBy(() -> service.create(alice.getId(),
                new WorkoutRequest(" ", start, null, null, null, null, null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsExerciseOfAnotherUserOrUnknown() {
        Exercise bobsPrivate = exercise("Titkos", ExerciseCategory.BODYWEIGHT, false, true, false, false, false, bob);
        var set = new WorkoutSetRequest(null, (short) 5, null, null, null, null);

        assertThatThrownBy(() -> service.create(alice.getId(), request(null,
                new WorkoutExerciseRequest(bobsPrivate.getId(), null, List.of(set)))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.create(alice.getId(), request(null,
                new WorkoutExerciseRequest(999_999L, null, List.of(set)))))
                .isInstanceOf(NotFoundException.class);

        // a saját privát gyakorlat használható
        Exercise alicesPrivate = exercise("Saját", ExerciseCategory.BODYWEIGHT, false, true, false, false, false, alice);
        assertThat(service.create(alice.getId(), request(null,
                new WorkoutExerciseRequest(alicesPrivate.getId(), null, List.of(set)))).exercises()).hasSize(1);
    }

    @Test
    void archivedExerciseIsRejectedForNewWorkoutsButKeptInExistingOnes() {
        WorkoutView existing = service.create(alice.getId(), request(null, squatBlock(strengthSet("100", 5))));
        em.flush();
        squat.setArchived(true);
        exercises.save(squat);

        assertThatThrownBy(() -> service.create(alice.getId(), request(null, squatBlock(strengthSet("100", 5)))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("archivált");

        // a már benne lévő archivált gyakorlat a módosításnál megmaradhat
        WorkoutView updated = service.update(alice.getId(), existing.id(),
                request(existing.version(), squatBlock(strengthSet("102.5", 5))));
        assertThat(updated.exercises()).hasSize(1);

        // másoláskor viszont kimarad
        assertThat(service.copy(alice.getId(), existing.id()).exercises()).isEmpty();
    }

    // ---- módosítás, törlés, másolás, lista ----

    @Test
    void updateReplacesContentsAndIncrementsVersion() {
        WorkoutView created = service.create(alice.getId(), request(null, squatBlock(strengthSet("100", 5))));
        em.flush();

        WorkoutRequest change = new WorkoutRequest("Új cím", created.startedAt(), created.endedAt(), (short) 8, "jegyzet",
                created.version(), List.of(new WorkoutExerciseRequest(pullUp.getId(), null,
                        List.of(new WorkoutSetRequest(null, (short) 6, null, null, null, null)))));
        WorkoutView updated = service.update(alice.getId(), created.id(), change);

        assertThat(updated.title()).isEqualTo("Új cím");
        assertThat(updated.exercises()).extracting(WorkoutView.ExerciseView::exerciseName).containsExactly("Húzódzkodás");
        assertThat(updated.version()).isGreaterThan(created.version());
    }

    @Test
    void concurrentModificationIsDetected() {
        WorkoutView created = service.create(alice.getId(), request(null, squatBlock(strengthSet("100", 5))));
        em.flush();
        WorkoutView first = service.update(alice.getId(), created.id(),
                new WorkoutRequest("Első mentés", created.startedAt(), created.endedAt(), (short) 7, null,
                        created.version(), List.of()));

        // a második szerkesztő a régi verzióval próbál menteni
        assertThatThrownBy(() -> service.update(alice.getId(), created.id(),
                new WorkoutRequest("Második mentés", created.startedAt(), created.endedAt(), (short) 7, null,
                        created.version(), List.of())))
                .isInstanceOf(StaleDataException.class);

        // a friss verzióval a mentés sikerül
        assertThat(service.update(alice.getId(), created.id(),
                new WorkoutRequest("Második mentés", created.startedAt(), created.endedAt(), (short) 7, null,
                        first.version(), List.of())).title()).isEqualTo("Második mentés");
    }

    @Test
    void updateRequiresVersion() {
        WorkoutView created = service.create(alice.getId(), request(null));
        assertThatThrownBy(() -> service.update(alice.getId(), created.id(), request(null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void deleteRemovesWorkoutWithItsSets() {
        Long id = service.create(alice.getId(), request(null, squatBlock(strengthSet("100", 5)))).id();
        em.flush();

        service.delete(alice.getId(), id);
        em.flush();

        assertThatThrownBy(() -> service.get(alice.getId(), id)).isInstanceOf(NotFoundException.class);
        Number sets = (Number) em.createNativeQuery("select count(*) from workout_sets").getSingleResult();
        assertThat(sets.longValue()).isZero();
    }

    @Test
    void copyCreatesOpenWorkoutWithSameExercisesAndSets() {
        WorkoutView source = service.create(alice.getId(), request(null,
                squatBlock(strengthSet("100", 5), strengthSet("105", 3))));
        em.flush();

        WorkoutView copy = service.copy(alice.getId(), source.id());

        assertThat(copy.id()).isNotEqualTo(source.id());
        assertThat(copy.endedAt()).isNull();
        assertThat(copy.fatigue()).isNull();
        assertThat(copy.exercises()).hasSize(1);
        assertThat(copy.exercises().get(0).sets()).extracting(WorkoutView.SetView::reps)
                .containsExactly((short) 5, (short) 3);
    }

    @Test
    void listsOnlyOwnWorkoutsNewestFirstWithPaging() {
        Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
        for (int i = 0; i < 3; i++) {
            Instant start = base.plus(i, ChronoUnit.DAYS);
            service.create(alice.getId(), new WorkoutRequest("Edzés " + i, start, null, null, null, null,
                    List.of(squatBlock(strengthSet("100", 5)))));
        }
        service.create(bob.getId(), request(null));
        em.flush();
        em.clear();

        Page<WorkoutSummary> page = service.list(alice.getId(), PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(WorkoutSummary::title).containsExactly("Edzés 2", "Edzés 1");
        assertThat(page.getContent().get(0).exerciseCount()).isEqualTo(1);
    }
}

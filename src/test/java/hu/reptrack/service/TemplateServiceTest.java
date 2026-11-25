package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.DuplicateTemplateNameException;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.TemplateExerciseRequest;
import hu.reptrack.dto.TemplateRequest;
import hu.reptrack.dto.TemplateView;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.PersonalRecordRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.repository.WorkoutRepository;
import hu.reptrack.repository.WorkoutTemplateRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Edzéssablonok (UC15): mentés, módosítás, törlés, és ugyanazok a gyakorlat-szabályok, mint az edzésnél. */
@Transactional
class TemplateServiceTest extends IntegrationTestBase {

    @Autowired TemplateService service;
    @Autowired ExerciseService exerciseService;
    @Autowired WorkoutTemplateRepository templates;
    @Autowired WorkoutRepository workouts;
    @Autowired PersonalRecordRepository records;
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
        squat = exercise("Teszt guggolás", ExerciseCategory.WEIGHTS, true, true, false, false, null);
        run = exercise("Teszt futás", ExerciseCategory.CARDIO, false, false, true, true, null);
        pullUp = exercise("Teszt húzódzkodás", ExerciseCategory.BODYWEIGHT, true, true, false, false, null);
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
                              boolean distance, User owner) {
        Exercise e = new Exercise();
        e.setName(name);
        e.setCategory(category);
        if (category == ExerciseCategory.CARDIO) {
            e.setCardioType(CardioType.RUNNING);
        }
        e.setTracksWeight(weight);
        e.setTracksReps(reps);
        e.setTracksDuration(duration);
        e.setTracksDistance(distance);
        e.setTracksHeartRate(category == ExerciseCategory.CARDIO);
        e.setOwner(owner);
        return exercises.save(e);
    }

    private static TemplateExerciseRequest strength(Exercise e, int sets, Integer reps, String kg) {
        return new TemplateExerciseRequest(e.getId(), (short) sets, reps == null ? null : reps.shortValue(),
                kg == null ? null : new BigDecimal(kg), null, null);
    }

    private static TemplateExerciseRequest cardio(Exercise e, Integer seconds, Integer meters) {
        return new TemplateExerciseRequest(e.getId(), (short) 1, null, null, seconds, meters);
    }

    private static TemplateRequest request(String name, TemplateExerciseRequest... rows) {
        return new TemplateRequest(name, List.of(rows));
    }

    // ---- létrehozás, lekérdezés, lista ----

    @Test
    void createsTemplateKeepingExerciseOrderAndPlannedValues() {
        TemplateView created = service.create(alice.getId(), request("  Láb A  ",
                strength(squat, 5, 5, "100"), cardio(run, 1800, 5000), strength(pullUp, 3, 8, null)));
        em.flush();
        em.clear();

        TemplateView loaded = service.get(alice.getId(), created.id());
        assertThat(loaded.name()).isEqualTo("Láb A");
        assertThat(loaded.exercises()).extracting(TemplateView.ExerciseView::exerciseName)
                .containsExactly("Teszt guggolás", "Teszt futás", "Teszt húzódzkodás");
        assertThat(loaded.exercises()).extracting(TemplateView.ExerciseView::position)
                .containsExactly((short) 1, (short) 2, (short) 3);
        TemplateView.ExerciseView first = loaded.exercises().get(0);
        assertThat(first.plannedSets()).isEqualTo((short) 5);
        assertThat(first.plannedReps()).isEqualTo((short) 5);
        assertThat(first.plannedWeightKg()).isEqualByComparingTo("100");
        assertThat(loaded.exercises().get(1).plannedDurationSec()).isEqualTo(1800);
        assertThat(loaded.exercises().get(1).plannedDistanceM()).isEqualTo(5000);
        assertThat(loaded.exercises().get(2).plannedWeightKg()).isNull();
    }

    @Test
    void listShowsOnlyOwnTemplatesByName() {
        service.create(alice.getId(), request("Hát", strength(pullUp, 3, 8, null)));
        service.create(alice.getId(), request("Áll", strength(squat, 3, 5, "80")));
        service.create(bob.getId(), request("Bob sablonja", strength(squat, 3, 5, "80")));
        em.flush();
        em.clear();

        assertThat(service.list(alice.getId())).extracting(TemplateView::name).containsExactly("Áll", "Hát");
        assertThat(service.list(bob.getId())).extracting(TemplateView::name).containsExactly("Bob sablonja");
    }

    @Test
    void plannedValuesAreOptionalOnlyTheNumberOfSetsIsRequired() {
        TemplateView view = service.create(alice.getId(), request("Laza terv",
                strength(squat, 3, null, null), cardio(run, null, null)));
        assertThat(view.exercises()).hasSize(2);
        assertThat(view.exercises().get(0).plannedReps()).isNull();
        assertThat(view.exercises().get(1).plannedDurationSec()).isNull();
    }

    @Test
    void sameExerciseMayAppearTwice() {
        TemplateView view = service.create(alice.getId(), request("Guggolós nap",
                strength(squat, 2, 10, "60"), strength(squat, 3, 5, "100")));
        assertThat(view.exercises()).hasSize(2);
    }

    // ---- módosítás, törlés ----

    @Test
    void updateReplacesNameAndExercises() {
        Long id = service.create(alice.getId(), request("Láb A", strength(squat, 5, 5, "100"), cardio(run, 600, null))).id();
        em.flush();
        em.clear();

        service.update(alice.getId(), id, request("Láb B", strength(pullUp, 4, 6, "10")));
        em.flush();
        em.clear();

        TemplateView loaded = service.get(alice.getId(), id);
        assertThat(loaded.name()).isEqualTo("Láb B");
        assertThat(loaded.exercises()).extracting(TemplateView.ExerciseView::exerciseName).containsExactly("Teszt húzódzkodás");
        assertThat(loaded.exercises().get(0).plannedWeightKg()).isEqualByComparingTo("10");
    }

    @Test
    void deleteRemovesTemplateWithItsExercises() {
        Long id = service.create(alice.getId(), request("Láb A", strength(squat, 5, 5, "100"))).id();
        em.flush();

        service.delete(alice.getId(), id);
        em.flush();

        assertThat(templates.findById(id)).isEmpty();
        assertThat(em.createQuery("select count(te) from TemplateExercise te", Long.class).getSingleResult()).isZero();
    }

    @Test
    void otherUserCannotTouchTemplate() {
        Long id = service.create(alice.getId(), request("Láb A", strength(squat, 5, 5, "100"))).id();

        assertThatThrownBy(() -> service.get(bob.getId(), id)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(bob.getId(), id, request("Átírva", strength(squat, 1, 1, "1"))))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(bob.getId(), id)).isInstanceOf(NotFoundException.class);
        assertThat(service.get(alice.getId(), id).name()).isEqualTo("Láb A");
    }

    // ---- szabályok ----

    @Test
    void templateNeedsAtLeastOneExercise() {
        assertThatThrownBy(() -> service.create(alice.getId(), request("Üres")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("legalább egy gyakorlat");
    }

    @Test
    void namesAreUniquePerUserIgnoringCase() {
        Long id = service.create(alice.getId(), request("Láb A", strength(squat, 5, 5, "100"))).id();
        service.create(alice.getId(), request("Hát", strength(pullUp, 3, 8, null)));

        assertThatThrownBy(() -> service.create(alice.getId(), request(" LÁB a ", strength(squat, 3, 5, "80"))))
                .isInstanceOf(DuplicateTemplateNameException.class);
        assertThatThrownBy(() -> service.update(alice.getId(), id, request("hát", strength(squat, 3, 5, "80"))))
                .isInstanceOf(DuplicateTemplateNameException.class);
        // a saját nevét megtarthatja, és más felhasználónál lehet ugyanaz a név
        service.update(alice.getId(), id, request("Láb A", strength(squat, 3, 5, "80")));
        service.create(bob.getId(), request("Láb A", strength(squat, 3, 5, "80")));
    }

    @Test
    void rejectsFieldsTheExerciseDoesNotTrack() {
        assertThatThrownBy(() -> service.create(alice.getId(), request("T",
                new TemplateExerciseRequest(squat.getId(), (short) 3, (short) 5, null, null, 5000))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("távolság");
        assertThatThrownBy(() -> service.create(alice.getId(), request("T",
                new TemplateExerciseRequest(run.getId(), (short) 1, null, new BigDecimal("10"), 600, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("súly");
        assertThatThrownBy(() -> service.create(alice.getId(), request("T",
                new TemplateExerciseRequest(run.getId(), (short) 1, (short) 5, null, 600, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ismétlésszám");
        assertThatThrownBy(() -> service.create(alice.getId(), request("T",
                new TemplateExerciseRequest(squat.getId(), (short) 3, (short) 5, null, 60, null))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("időtartam");
    }

    @Test
    void someoneElsesPrivateExerciseIsNotFound() {
        Exercise bobs = exercise("Bob gyakorlata", ExerciseCategory.WEIGHTS, true, true, false, false, bob);
        assertThatThrownBy(() -> service.create(alice.getId(), request("T", strength(bobs, 3, 5, "50"))))
                .isInstanceOf(NotFoundException.class);
        // a sajátját bárki használhatja
        assertThat(service.create(bob.getId(), request("T", strength(bobs, 3, 5, "50"))).exercises()).hasSize(1);
    }

    @Test
    void archivedExerciseCannotBeAddedButMayStayInTheTemplate() {
        Long id = service.create(alice.getId(), request("Láb A", strength(squat, 5, 5, "100"))).id();
        squat.setArchived(true);
        exercises.flush();

        assertThatThrownBy(() -> service.create(alice.getId(), request("Láb B", strength(squat, 3, 5, "80"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("archivált");
        // a már benne lévő archivált gyakorlat a módosításkor maradhat, és a nézet jelzi az archiváltságot
        TemplateView updated = service.update(alice.getId(), id, request("Láb A", strength(squat, 4, 5, "105")));
        assertThat(updated.exercises().get(0).archived()).isTrue();
    }

    @Test
    void beanValidationGuardsTheRanges() {
        assertThatThrownBy(() -> service.create(alice.getId(), request(" ", strength(squat, 3, 5, "100"))))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.create(alice.getId(), request("T", strength(squat, 21, 5, "100"))))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.create(alice.getId(), request("T", strength(squat, 3, 101, "100"))))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.create(alice.getId(), request("T", strength(squat, 3, 5, "500.5"))))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> service.create(alice.getId(), request("T",
                new TemplateExerciseRequest(squat.getId(), null, (short) 5, null, null, null))))
                .isInstanceOf(ConstraintViolationException.class);
    }

    // ---- kapcsolat a többi funkcióval ----

    @Test
    void templateIsAPlanNotTrainingData() {
        service.create(alice.getId(), request("Láb A", strength(squat, 5, 5, "200")));
        em.flush();

        assertThat(workouts.count()).isZero();
        assertThat(records.count()).isZero();
    }

    @Test
    void privateExerciseUsedInATemplateCanOnlyBeArchived() {
        Exercise own = exercise("Alice gyakorlata", ExerciseCategory.WEIGHTS, true, true, false, false, alice);
        Long id = service.create(alice.getId(), request("Saját", strength(own, 3, 5, "50"))).id();
        em.flush();

        assertThatThrownBy(() -> exerciseService.deleteOwn(alice.getId(), own.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("sablonban");

        // a sablon törlése után már törölhető
        service.delete(alice.getId(), id);
        em.flush();
        exerciseService.deleteOwn(alice.getId(), own.getId());
        em.flush();
        assertThat(exercises.findById(own.getId())).isEmpty();
    }
}

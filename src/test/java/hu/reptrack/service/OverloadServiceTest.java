package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.Granularity;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.OverloadRule;
import hu.reptrack.dto.OverloadWarningView;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.dto.WorkoutView;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.rules.Periods;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * A túlterhelési figyelmeztetések a WorkoutService-be kötve, valódi adatbázissal. A dátumok a mai naphoz képest
 * értendők, mert a szabályok csak az utolsó 90 nap eseményeit kezelik.
 */
@Transactional
class OverloadServiceTest extends IntegrationTestBase {

    static final ZoneId ZONE = ZoneId.of("Europe/Budapest");

    @Autowired OverloadService overload;
    @Autowired WorkoutService workouts;
    @Autowired UserRepository users;
    @Autowired ExerciseRepository exercises;
    @Autowired EntityManager em;

    User alice;
    User bob;
    Exercise squat;       // elsődleges izom: QUADS

    final LocalDate today = LocalDate.now(ZONE);

    @BeforeEach
    void setUp() {
        alice = users.save(user("alice@example.com"));
        bob = users.save(user("bob@example.com"));
        squat = new Exercise();
        squat.setName("Teszt guggolás");
        squat.setCategory(ExerciseCategory.WEIGHTS);
        squat.setPrimaryMuscle(MuscleGroup.QUADS);
        squat.setTracksWeight(true);
        squat.setTracksReps(true);
        exercises.save(squat);
    }

    private User user(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setDisplayName("Teszt");
        return u;
    }

    private Instant at(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(ZONE).toInstant();
    }

    /** Lezárt edzés adott napon, 18:00-tól {@code minutes} percig, {@code sets} darab szettel a guggolásból. */
    private WorkoutView workout(User user, LocalDate day, int minutes, int fatigue, int sets) {
        Instant start = at(day, 18);
        var set = new WorkoutSetRequest(new BigDecimal("60"), (short) 5, null, null, null, null);
        var block = sets == 0 ? List.<WorkoutExerciseRequest>of()
                : List.of(new WorkoutExerciseRequest(squat.getId(), null, java.util.Collections.nCopies(sets, set)));
        return workouts.create(user.getId(), new WorkoutRequest("Edzés", start, start.plusSeconds(minutes * 60L),
                (short) fatigue, null, null, block));
    }

    private List<OverloadWarningView> open(User user) {
        return overload.list(user.getId(), false);
    }

    private List<OverloadWarningView> openOf(User user, OverloadRule rule) {
        return open(user).stream().filter(w -> w.rule() == rule).toList();
    }

    // ---- magas fáradtság ----

    @Test
    void threeHighFatigueWorkoutsInARowCreateAWarningAtTheThirdSave() {
        workout(alice, today.minusDays(3), 30, 9, 0);
        workout(alice, today.minusDays(2), 30, 9, 0);
        assertThat(open(alice)).isEmpty();

        WorkoutView third = workout(alice, today.minusDays(1), 30, 9, 0);

        List<OverloadWarningView> warnings = open(alice);
        assertThat(warnings).hasSize(1);
        OverloadWarningView w = warnings.get(0);
        assertThat(w.rule()).isEqualTo(OverloadRule.HIGH_FATIGUE);
        assertThat(w.workoutId()).isEqualTo(third.id());
        assertThat(w.eventDate()).isEqualTo(today.minusDays(1));
        assertThat(w.acknowledged()).isFalse();
        assertThat(w.message()).contains("Három egymást követő edzésed");
        assertThat(overload.forWorkout(alice.getId(), third.id())).hasSize(1);
    }

    @Test
    void editingTheWorkoutToLowerFatigueRemovesTheWarning() {
        workout(alice, today.minusDays(3), 30, 9, 0);
        workout(alice, today.minusDays(2), 30, 9, 0);
        WorkoutView third = workout(alice, today.minusDays(1), 30, 9, 0);
        assertThat(open(alice)).hasSize(1);
        em.flush();

        workouts.update(alice.getId(), third.id(), new WorkoutRequest(third.title(), third.startedAt(), third.endedAt(),
                (short) 5, null, third.version(), List.of()));

        assertThat(open(alice)).isEmpty();
    }

    @Test
    void deletingTheWorkoutRemovesTheWarning() {
        workout(alice, today.minusDays(3), 30, 9, 0);
        workout(alice, today.minusDays(2), 30, 9, 0);
        WorkoutView third = workout(alice, today.minusDays(1), 30, 9, 0);
        assertThat(open(alice)).hasSize(1);

        workouts.delete(alice.getId(), third.id());

        assertThat(overload.list(alice.getId(), true)).isEmpty();
    }

    // ---- nyugtázás ----

    @Test
    void acknowledgedWarningStaysAcknowledgedWhenRulesAreEvaluatedAgain() {
        workout(alice, today.minusDays(3), 30, 9, 0);
        workout(alice, today.minusDays(2), 30, 9, 0);
        workout(alice, today.minusDays(1), 30, 9, 0);
        Long id = open(alice).get(0).id();

        overload.acknowledge(alice.getId(), id);
        assertThat(open(alice)).isEmpty();

        workout(alice, today.minusDays(10), 30, 3, 0);          // egy másik mentés újra kiértékeli a szabályokat

        assertThat(open(alice)).isEmpty();
        List<OverloadWarningView> all = overload.list(alice.getId(), true);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).acknowledged()).isTrue();
        assertThat(all.get(0).id()).isEqualTo(id);              // ugyanaz a sor, nem jött létre újra
    }

    @Test
    void evaluationIsIdempotent() {
        workout(alice, today.minusDays(3), 30, 9, 0);
        workout(alice, today.minusDays(2), 30, 9, 0);
        workout(alice, today.minusDays(1), 30, 9, 0);

        assertThat(overload.evaluate(alice.getId())).isEmpty();             // nincs új
        assertThat(overload.list(alice.getId(), true)).hasSize(1);
    }

    @Test
    void otherUsersCannotAcknowledgeOrSeeWarnings() {
        workout(alice, today.minusDays(3), 30, 9, 0);
        workout(alice, today.minusDays(2), 30, 9, 0);
        workout(alice, today.minusDays(1), 30, 9, 0);
        Long id = open(alice).get(0).id();

        assertThatThrownBy(() -> overload.acknowledge(bob.getId(), id)).isInstanceOf(NotFoundException.class);
        assertThat(overload.list(bob.getId(), true)).isEmpty();
        assertThat(open(alice)).hasSize(1);
    }

    // ---- küszöbök ----

    @Test
    void usersOwnThresholdIsUsed() {
        alice.setFatigueThreshold((short) 10);
        users.saveAndFlush(alice);

        workout(alice, today.minusDays(3), 30, 9, 0);
        workout(alice, today.minusDays(2), 30, 9, 0);
        workout(alice, today.minusDays(1), 30, 9, 0);

        assertThat(open(alice)).isEmpty();
    }

    // ---- izomcsoport ----

    @Test
    void sameMuscleThreeDaysInARowCreatesAWarning() {
        workout(alice, today.minusDays(3), 40, 4, 6);
        workout(alice, today.minusDays(2), 40, 4, 6);
        assertThat(openOf(alice, OverloadRule.MUSCLE_SETS)).isEmpty();

        WorkoutView third = workout(alice, today.minusDays(1), 40, 4, 7);

        List<OverloadWarningView> warnings = openOf(alice, OverloadRule.MUSCLE_SETS);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).workoutId()).isEqualTo(third.id());
        assertThat(warnings.get(0).message()).contains("combfeszítő");
    }

    @Test
    void fiveSetsADayDoNotTriggerTheMuscleRule() {
        workout(alice, today.minusDays(3), 40, 4, 5);
        workout(alice, today.minusDays(2), 40, 4, 5);
        workout(alice, today.minusDays(1), 40, 4, 5);
        assertThat(openOf(alice, OverloadRule.MUSCLE_SETS)).isEmpty();
    }

    // ---- heti terhelés ----

    @Test
    void weeklyLoadWellAboveTheAverageCreatesAWarning() {
        LocalDate thisMonday = Periods.start(today, Granularity.WEEK);
        for (int weeksAgo = 4; weeksAgo >= 1; weeksAgo--) {
            workout(alice, thisMonday.minusWeeks(weeksAgo), 100, 6, 0);          // 600 hetente
        }
        assertThat(openOf(alice, OverloadRule.WEEKLY_LOAD)).isEmpty();

        WorkoutView big = workout(alice, thisMonday, 150, 6, 0);                 // 900 > 1,3 × 600

        List<OverloadWarningView> warnings = openOf(alice, OverloadRule.WEEKLY_LOAD);
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).workoutId()).isEqualTo(big.id());
        assertThat(warnings.get(0).message()).contains("900").contains("600");
    }

    // ---- időablak ----

    @Test
    void eventsOlderThanTheHorizonDoNotCreateWarnings() {
        LocalDate longAgo = today.minusDays(OverloadService.HORIZON_DAYS + 110);
        workout(alice, longAgo, 30, 9, 0);
        workout(alice, longAgo.plusDays(1), 30, 9, 0);
        workout(alice, longAgo.plusDays(2), 30, 9, 0);

        assertThat(overload.list(alice.getId(), true)).isEmpty();
    }

    // ---- több szabály ugyanazon az edzésen ----

    @Test
    void severalRulesCanWarnForTheSameWorkout() {
        List<WorkoutView> created = new ArrayList<>();
        created.add(workout(alice, today.minusDays(3), 40, 9, 6));
        created.add(workout(alice, today.minusDays(2), 40, 9, 6));
        WorkoutView third = workout(alice, today.minusDays(1), 40, 9, 6);

        List<OverloadWarningView> forThird = overload.forWorkout(alice.getId(), third.id());
        assertThat(forThird).extracting(OverloadWarningView::rule)
                .containsExactlyInAnyOrder(OverloadRule.HIGH_FATIGUE, OverloadRule.MUSCLE_SETS);
    }
}

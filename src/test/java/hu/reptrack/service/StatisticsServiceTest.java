package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.BodyWeightEntry;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.Goal;
import hu.reptrack.domain.User;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseProgressPoint;
import hu.reptrack.dto.GoalProgress;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.Granularity;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.PeriodSummary;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.repository.BodyWeightEntryRepository;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.GoalRepository;
import hu.reptrack.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class StatisticsServiceTest extends IntegrationTestBase {

    static final ZoneId ZONE = ZoneId.of("Europe/Budapest");

    @Autowired StatisticsService statistics;
    @Autowired WorkoutService workouts;
    @Autowired UserRepository users;
    @Autowired ExerciseRepository exercises;
    @Autowired BodyWeightEntryRepository bodyWeights;
    @Autowired GoalRepository goals;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    User alice;
    User bob;
    Exercise squat;
    Exercise run;

    @BeforeEach
    void setUp() {
        alice = users.save(user("alice@example.com"));
        bob = users.save(user("bob@example.com"));
        squat = exercise("Teszt guggolás", ExerciseCategory.WEIGHTS, true, true, false, null, null);
        run = exercise("Teszt futás", ExerciseCategory.CARDIO, false, false, true, CardioType.RUNNING, null);
    }

    private User user(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setDisplayName("Teszt");
        return u;
    }

    private Exercise exercise(String name, ExerciseCategory category, boolean weight, boolean reps, boolean duration,
                              CardioType cardioType, User owner) {
        Exercise e = new Exercise();
        e.setName(name);
        e.setCategory(category);
        e.setCardioType(cardioType);
        e.setTracksWeight(weight);
        e.setTracksReps(reps);
        e.setTracksDuration(duration);
        e.setTracksDistance(duration);
        e.setOwner(owner);
        return exercises.save(e);
    }

    private void weightWorkout(User user, String start, int minutes, int fatigue, String kg, int reps) {
        var begin = LocalDateTime.parse(start).atZone(ZONE).toInstant();
        var set = new WorkoutSetRequest(new BigDecimal(kg), (short) reps, null, null, null, null);
        workouts.create(user.getId(), new WorkoutRequest("Erő", begin, begin.plusSeconds(minutes * 60L),
                (short) fatigue, null, null, List.of(new WorkoutExerciseRequest(squat.getId(), null, List.of(set, set)))));
    }

    private void runWorkout(User user, String start, int minutes, int fatigue, int durationSec, int distanceM) {
        var begin = LocalDateTime.parse(start).atZone(ZONE).toInstant();
        var set = new WorkoutSetRequest(null, null, durationSec, distanceM, null, null);
        workouts.create(user.getId(), new WorkoutRequest("Futás", begin, begin.plusSeconds(minutes * 60L),
                (short) fatigue, null, null, List.of(new WorkoutExerciseRequest(run.getId(), null, List.of(set)))));
    }

    private void bodyWeight(User user, String date, String kg) {
        BodyWeightEntry entry = new BodyWeightEntry();
        entry.setUser(user);
        entry.setMeasuredOn(LocalDate.parse(date));
        entry.setWeightKg(new BigDecimal(kg));
        bodyWeights.save(entry);
    }

    @Test
    void weeklySummaryOverRealData() {
        weightWorkout(alice, "2026-11-09T18:00:00", 60, 7, "100", 5);   // 2 szett × 500 = 1000 kg, terhelés 420
        runWorkout(alice, "2026-11-11T07:00:00", 30, 6, 1800, 5000);    // 5 km, terhelés 180
        weightWorkout(alice, "2026-11-17T18:00:00", 45, 8, "90", 5);    // a következő hét
        weightWorkout(bob, "2026-11-10T18:00:00", 60, 9, "200", 5);     // másik felhasználó: nem számít

        List<PeriodSummary> weeks = statistics.summaries(alice.getId(), Granularity.WEEK, null, null, null, null);

        assertThat(weeks).hasSize(2);
        PeriodSummary first = weeks.get(0);
        assertThat(first.periodStart()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(first.workouts()).isEqualTo(2);
        assertThat(first.totalMinutes()).isEqualByComparingTo("90.0");
        assertThat(first.totalVolumeKg()).isEqualByComparingTo("1000.0");
        assertThat(first.distanceKm()).isEqualByComparingTo("5.00");
        assertThat(first.trainingLoad()).isEqualByComparingTo("600.0");
        assertThat(first.setsByCategory()).containsEntry(ExerciseCategory.WEIGHTS, 2).containsEntry(ExerciseCategory.CARDIO, 1);
        assertThat(weeks.get(1).totalVolumeKg()).isEqualByComparingTo("900.0");
    }

    @Test
    void filtersByCategoryExerciseAndPeriod() {
        weightWorkout(alice, "2026-11-09T18:00:00", 60, 7, "100", 5);
        runWorkout(alice, "2026-11-11T07:00:00", 30, 6, 1800, 5000);
        runWorkout(alice, "2026-11-18T07:00:00", 30, 6, 1800, 5000);

        // csak a kardió: a súlyzós edzés kimarad, a hét 0 edzéssel jelenik meg
        var cardio = statistics.summaries(alice.getId(), Granularity.WEEK, null, null, null, ExerciseCategory.CARDIO);
        assertThat(cardio).extracting(PeriodSummary::workouts).containsExactly(1, 1);
        assertThat(cardio.get(0).totalVolumeKg()).isEqualByComparingTo("0");

        // egy gyakorlat + időszak (a záró nap is beletartozik)
        var squatOnly = statistics.summaries(alice.getId(), Granularity.WEEK, LocalDate.of(2026, 11, 9),
                LocalDate.of(2026, 11, 15), squat.getId(), null);
        assertThat(squatOnly).hasSize(1);
        assertThat(squatOnly.get(0).workouts()).isEqualTo(1);

        var lastDay = statistics.summaries(alice.getId(), Granularity.WEEK, LocalDate.of(2026, 11, 18),
                LocalDate.of(2026, 11, 18), null, null);
        assertThat(lastDay).hasSize(1);
        assertThat(lastDay.get(0).workouts()).isEqualTo(1);
    }

    @Test
    void exerciseProgressCurve() {
        weightWorkout(alice, "2026-11-02T18:00:00", 60, 7, "100", 5);
        weightWorkout(alice, "2026-11-09T18:00:00", 60, 7, "110", 5);
        bodyWeight(alice, "2026-11-01", "80");

        List<ExerciseProgressPoint> points = statistics.exerciseProgress(alice.getId(), squat.getId(), null, null);

        assertThat(points).hasSize(2);
        assertThat(points).extracting(ExerciseProgressPoint::estimated1Rm)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("116.7"), new BigDecimal("128.3"));
        assertThat(points.get(1).relativeStrength()).isEqualByComparingTo("1.60");   // 128,3 / 80
    }

    @Test
    void runningProgressHasPace() {
        runWorkout(alice, "2026-11-03T07:00:00", 30, 6, 1500, 5000);
        var points = statistics.exerciseProgress(alice.getId(), run.getId(), null, null);
        assertThat(points.get(0).paceMinPerKm()).isEqualByComparingTo("5.00");
        assertThat(points.get(0).speedKmh()).isNull();
    }

    @Test
    void anotherUsersPrivateExerciseIsNotAvailable() {
        Exercise bobsPrivate = exercise("Titkos", ExerciseCategory.BODYWEIGHT, false, true, false, null, bob);
        assertThatThrownBy(() -> statistics.exerciseProgress(alice.getId(), bobsPrivate.getId(), null, null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> statistics.summaries(alice.getId(), Granularity.WEEK, null, null, bobsPrivate.getId(), null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> statistics.exerciseProgress(alice.getId(), 999_999L, null, null))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void rejectsInvertedRange() {
        assertThatThrownBy(() -> statistics.summaries(alice.getId(), Granularity.WEEK, LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 1), null, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> statistics.bodyWeightSeries(alice.getId(), LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 11, 1))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void bodyWeightSeriesIsFilteredAndOwn() {
        bodyWeight(alice, "2026-10-01", "82");
        bodyWeight(alice, "2026-11-01", "81");
        bodyWeight(alice, "2026-11-15", "80");
        bodyWeight(bob, "2026-11-01", "99");

        assertThat(statistics.bodyWeightSeries(alice.getId(), null, null)).hasSize(3);
        assertThat(statistics.bodyWeightSeries(alice.getId(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)))
                .extracting(p -> p.weightKg().intValue()).containsExactly(81, 80);
    }

    @Test
    void goalProgressForBodyWeightAndStrength() {
        bodyWeight(alice, "2026-11-01", "90");
        bodyWeight(alice, "2026-11-20", "85");
        weightWorkout(alice, "2026-11-09T18:00:00", 60, 7, "90", 5);

        Goal loss = new Goal();
        loss.setUser(alice);
        loss.setGoalType(GoalType.WEIGHT_LOSS);
        loss.setTargetValue(new BigDecimal("80"));
        goals.save(loss);

        Goal lift = new Goal();
        lift.setUser(alice);
        lift.setGoalType(GoalType.STRENGTH);
        lift.setExercise(squat);
        lift.setTargetValue(new BigDecimal("100"));
        goals.save(lift);

        Goal inactive = new Goal();
        inactive.setUser(alice);
        inactive.setGoalType(GoalType.WEIGHT_LOSS);
        inactive.setTargetValue(new BigDecimal("70"));
        inactive.setActive(false);
        goals.save(inactive);
        em.flush();
        em.clear();

        List<GoalProgress> progress = statistics.goalProgress(alice.getId());

        assertThat(progress).hasSize(2);   // az inaktív cél kimarad
        GoalProgress weight = progress.stream().filter(p -> p.type() == GoalType.WEIGHT_LOSS).findFirst().orElseThrow();
        // a cél a mérések után jött létre (ma), ezért a kezdőérték a legutóbbi mérés
        assertThat(weight.current()).isEqualByComparingTo("85");
        GoalProgress strength = progress.stream().filter(p -> p.type() == GoalType.STRENGTH).findFirst().orElseThrow();
        assertThat(strength.current()).isEqualByComparingTo("90");
        assertThat(strength.percent()).isEqualByComparingTo("90.0");
    }

    /** A specifikáció elvárása: kb. 1000 edzés adatával a statisztika 2 másodpercen belül elkészül. */
    @Test
    void statisticsOfAThousandWorkoutsAreComputedWithinTwoSeconds() {
        jdbc.update("insert into workouts (user_id, title, started_at, ended_at, fatigue) "
                + "select ?, 'W' || g, timestamptz '2024-01-01 18:00+01' + g * interval '1 day', "
                + "timestamptz '2024-01-01 19:00+01' + g * interval '1 day', 1 + g % 10 "
                + "from generate_series(1, 1000) g", alice.getId());
        jdbc.update("insert into workout_exercises (workout_id, exercise_id, position) "
                + "select w.id, ?, 1 from workouts w where w.user_id = ?", squat.getId(), alice.getId());
        jdbc.update("insert into workout_sets (workout_exercise_id, position, weight_kg, reps) "
                + "select we.id, s, 60 + s, 5 from workout_exercises we cross join generate_series(1, 20) s");
        for (int i = 0; i < 100; i++) {
            bodyWeight(alice, LocalDate.of(2024, 1, 1).plusDays(i * 10L).toString(), "80");
        }
        em.flush();
        em.clear();

        // bemelegítő hívás (a lekérdezések fordítása nem a mért időbe tartozik)
        statistics.summaries(alice.getId(), Granularity.WEEK, null, null, null, null);

        long start = System.nanoTime();
        List<PeriodSummary> weeks = statistics.summaries(alice.getId(), Granularity.WEEK, null, null, null, null);
        List<ExerciseProgressPoint> curve = statistics.exerciseProgress(alice.getId(), squat.getId(), null, null);
        long millis = (System.nanoTime() - start) / 1_000_000;

        assertThat(weeks.stream().mapToInt(PeriodSummary::workouts).sum()).isEqualTo(1000);
        assertThat(curve).hasSize(1000);
        assertThat(millis).as("a statisztika számítási ideje ms-ban").isLessThan(2000);
    }
}

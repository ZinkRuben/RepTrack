package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Goal;
import hu.reptrack.domain.User;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.EnduranceMetric;
import hu.reptrack.dto.GoalProgress;
import hu.reptrack.dto.GoalRequest;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.GoalView;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.GoalRepository;
import hu.reptrack.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Edzéscélok (UC2): a felvétel szabályai céltípusonként, és a haladás valódi edzésekből (az állóképességi céloké is). */
@Transactional
class GoalServiceTest extends IntegrationTestBase {

    @Autowired ProfileService profiles;
    @Autowired StatisticsService statistics;
    @Autowired WorkoutService workouts;
    @Autowired GoalRepository goals;
    @Autowired ExerciseRepository exercises;
    @Autowired UserRepository users;

    User anna;
    Long run;        // Futás: idő + táv
    Long plank;      // Alkartámasz: csak idő
    Long squat;      // Guggolás: súly + ismétlés

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("anna@example.com");
        u.setPasswordHash("hash");
        u.setDisplayName("Anna");
        anna = users.save(u);
        run = shared("Futás");
        plank = shared("Alkartámasz");
        squat = shared("Guggolás");
    }

    private Long shared(String name) {
        return exercises.findAll().stream().filter(e -> e.isShared() && e.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }

    private GoalRequest endurance(EnduranceMetric metric, Long exercise, String target, Integer distanceM) {
        return new GoalRequest(GoalType.ENDURANCE, exercise, target == null ? null : new BigDecimal(target), null,
                metric, distanceM);
    }

    private void runWorkout(int daysAgo, int seconds, int meters) {
        Instant start = Instant.now().minus(daysAgo, ChronoUnit.DAYS);
        workouts.create(anna.getId(), new WorkoutRequest("Futás", start, start.plus(1, ChronoUnit.HOURS), (short) 5, null,
                null, List.of(new WorkoutExerciseRequest(run, null,
                        List.of(new WorkoutSetRequest(null, null, seconds, meters, null, null))))));
    }

    private GoalProgress onlyProgress() {
        List<GoalProgress> all = statistics.goalProgress(anna.getId());
        assertThat(all).hasSize(1);
        return all.get(0);
    }

    // ---- felvétel ----

    @Test
    void enduranceGoalStoresItsMetricAndDistance() {
        profiles.addGoal(anna.getId(), endurance(EnduranceMetric.TIME_FOR_DISTANCE, run, "25", 5000));

        GoalView view = profiles.goals(anna.getId()).get(0);
        assertThat(view.enduranceMetric()).isEqualTo(EnduranceMetric.TIME_FOR_DISTANCE);
        assertThat(view.targetDistanceM()).isEqualTo(5000);
        assertThat(view.targetValue()).isEqualByComparingTo("25");
    }

    @Test
    void enduranceGoalWithoutAMetricMeansDistance() {
        profiles.addGoal(anna.getId(), endurance(null, run, "10", null));
        assertThat(profiles.goals(anna.getId()).get(0).enduranceMetric()).isEqualTo(EnduranceMetric.DISTANCE);
    }

    @Test
    void otherGoalTypesNeverGetAMetricOrDistance() {
        profiles.addGoal(anna.getId(), new GoalRequest(GoalType.STRENGTH, squat, new BigDecimal("100"), null,
                EnduranceMetric.DISTANCE, 5000));
        Goal saved = goals.findAll().get(0);
        assertThat(saved.getEnduranceMetric()).isNull();
        assertThat(saved.getTargetDistanceM()).isNull();
    }

    @Test
    void timeForDistanceNeedsBothTheDistanceAndTheTime() {
        assertThatThrownBy(() -> profiles.addGoal(anna.getId(), endurance(EnduranceMetric.TIME_FOR_DISTANCE, run, "25", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("a távot és a célidőt is");
        assertThatThrownBy(() -> profiles.addGoal(anna.getId(), endurance(EnduranceMetric.TIME_FOR_DISTANCE, run, null, 5000)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("a távot és a célidőt is");
    }

    @Test
    void enduranceTargetNeedsAnExercise() {
        assertThatThrownBy(() -> profiles.addGoal(anna.getId(), endurance(EnduranceMetric.DISTANCE, null, "10", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("válassz gyakorlatot");
    }

    @Test
    void theExerciseMustRecordWhatTheGoalMeasures() {
        assertThatThrownBy(() -> profiles.addGoal(anna.getId(), endurance(EnduranceMetric.DISTANCE, squat, "10", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("táv rögzíthető");
        assertThatThrownBy(() -> profiles.addGoal(anna.getId(), endurance(EnduranceMetric.DISTANCE, plank, "10", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Alkartámasz");
        assertThatThrownBy(() -> profiles.addGoal(anna.getId(), endurance(EnduranceMetric.TIME_FOR_DISTANCE, plank, "5", 1000)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("táv és időtartam");
        // ami illik, az felvehető
        profiles.addGoal(anna.getId(), endurance(EnduranceMetric.DURATION, plank, "5", null));
        profiles.addGoal(anna.getId(), endurance(EnduranceMetric.DURATION, run, "60", null));
        assertThat(profiles.goals(anna.getId())).hasSize(2);
    }

    // ---- haladás valódi edzésekből ----

    @Test
    void distanceGoalProgressComesFromTheLongestRun() {
        profiles.addGoal(anna.getId(), endurance(EnduranceMetric.DISTANCE, run, "10", null));
        runWorkout(5, 1800, 5000);
        runWorkout(2, 2400, 7500);

        GoalProgress progress = onlyProgress();
        assertThat(progress.current()).isEqualByComparingTo("7.5");
        assertThat(progress.percent()).isEqualByComparingTo("75.0");
    }

    @Test
    void timeForDistanceGoalProgressComesFromTheBestEquivalentTime() {
        runWorkout(10, 1800, 5000);                       // a cél előtt: 5 km 30:00
        profiles.addGoal(anna.getId(), endurance(EnduranceMetric.TIME_FOR_DISTANCE, run, "25", 5000));
        runWorkout(0, 1716, 5200);                        // 5,2 km 28:36 = 5 km 27:30

        GoalProgress progress = onlyProgress();
        assertThat(progress.start()).isEqualByComparingTo("30");
        assertThat(progress.current()).isEqualByComparingTo("27.5");
        assertThat(progress.percent()).isEqualByComparingTo("50.0");
    }
}

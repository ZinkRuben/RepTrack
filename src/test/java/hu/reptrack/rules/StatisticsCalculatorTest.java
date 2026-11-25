package hu.reptrack.rules;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.dto.BodyWeightPoint;
import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.EnduranceMetric;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseProgressPoint;
import hu.reptrack.dto.GoalProgress;
import hu.reptrack.dto.GoalSnapshot;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.Granularity;
import hu.reptrack.dto.PeriodSummary;
import hu.reptrack.dto.SetRecord;
import hu.reptrack.dto.WorkoutRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatisticsCalculatorTest {

    static final ZoneId ZONE = ZoneId.of("Europe/Budapest");
    static final LocalDate NOV_9 = LocalDate.of(2026, 11, 9);   // hétfő

    // ---- segédek ----

    static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant();
    }

    static BigDecimal kg(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    static SetRecord strength(long workoutId, String start, String kg, int reps) {
        return new SetRecord(workoutId, at(start), null, null, 1L, "Guggolás", ExerciseCategory.WEIGHTS, null,
                kg(kg), (short) reps, null, null);
    }

    static SetRecord cardio(long workoutId, String start, CardioType type, int durationSec, int distanceM) {
        return new SetRecord(workoutId, at(start), null, null, 2L, "Kardió", ExerciseCategory.CARDIO, type,
                null, null, durationSec, distanceM);
    }

    static SetRecord bodyweight(long workoutId, String start, String externalKg, int reps) {
        return new SetRecord(workoutId, at(start), null, null, 3L, "Húzódzkodás", ExerciseCategory.BODYWEIGHT, null,
                kg(externalKg), (short) reps, null, null);
    }

    static WorkoutRow workout(long id, String start, String end, Integer fatigue) {
        return new WorkoutRow(id, at(start), end == null ? null : at(end), fatigue == null ? null : fatigue.shortValue());
    }

    static BodyWeightPoint bw(String date, String weight) {
        return new BodyWeightPoint(LocalDate.parse(date), new BigDecimal(weight));
    }

    // ---- összesítők ----

    List<WorkoutRow> sampleWorkouts() {
        return List.of(
                workout(1, "2026-11-09T18:00:00", "2026-11-09T19:00:00", 7),    // hétfő, 60 perc, terhelés 420
                workout(2, "2026-11-15T23:30:00", "2026-11-16T00:30:00", 5),    // vasárnap indul, éjfélen átnyúlik: 300
                workout(3, "2026-11-18T07:00:00", null, null));                 // nyitott edzés, szett nélkül
    }

    List<SetRecord> sampleSets() {
        return List.of(
                strength(1, "2026-11-09T18:00:00", "100", 5),
                strength(1, "2026-11-09T18:00:00", "100", 5),
                cardio(2, "2026-11-15T23:30:00", CardioType.RUNNING, 1800, 5000));
    }

    @Test
    void summarizesByWeekAndAssignsMidnightCrossingWorkoutToItsStartWeek() {
        List<PeriodSummary> weeks = StatisticsCalculator.summarize(sampleWorkouts(), sampleSets(), List.of(),
                Granularity.WEEK, ZONE, null, null);

        assertThat(weeks).extracting(PeriodSummary::periodStart).containsExactly(NOV_9, LocalDate.of(2026, 11, 16));

        PeriodSummary first = weeks.get(0);
        assertThat(first.workouts()).isEqualTo(2);                    // a vasárnap esti is ide tartozik
        assertThat(first.totalMinutes()).isEqualByComparingTo("120.0");
        assertThat(first.trainingLoad()).isEqualByComparingTo("720.0");
        assertThat(first.totalVolumeKg()).isEqualByComparingTo("1000.0");
        assertThat(first.distanceKm()).isEqualByComparingTo("5.00");
        assertThat(first.setsByCategory()).containsEntry(ExerciseCategory.WEIGHTS, 2)
                .containsEntry(ExerciseCategory.CARDIO, 1).containsEntry(ExerciseCategory.BODYWEIGHT, 0);
    }

    @Test
    void openWorkoutIsCountedButNotInTimeOrLoad() {
        PeriodSummary second = StatisticsCalculator.summarize(sampleWorkouts(), sampleSets(), List.of(),
                Granularity.WEEK, ZONE, null, null).get(1);

        assertThat(second.workouts()).isEqualTo(1);
        assertThat(second.totalMinutes()).isEqualByComparingTo("0");
        assertThat(second.trainingLoad()).isEqualByComparingTo("0");
    }

    @Test
    void fillsEmptyPeriodsBetweenFromAndTo() {
        List<PeriodSummary> weeks = StatisticsCalculator.summarize(sampleWorkouts(), sampleSets(), List.of(),
                Granularity.WEEK, ZONE, NOV_9, LocalDate.of(2026, 11, 27));

        assertThat(weeks).extracting(PeriodSummary::periodStart)
                .containsExactly(NOV_9, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 23));
        PeriodSummary empty = weeks.get(2);
        assertThat(empty.workouts()).isZero();
        assertThat(empty.totalVolumeKg()).isEqualByComparingTo("0");
        assertThat(empty.setsByCategory().values()).containsOnly(0);
    }

    @Test
    void summarizesByMonth() {
        List<PeriodSummary> months = StatisticsCalculator.summarize(sampleWorkouts(), sampleSets(), List.of(),
                Granularity.MONTH, ZONE, null, null);

        assertThat(months).hasSize(1);
        assertThat(months.get(0).periodStart()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(months.get(0).workouts()).isEqualTo(3);
    }

    @Test
    void emptyInputGivesEmptyResultWithoutRange() {
        assertThat(StatisticsCalculator.summarize(List.of(), List.of(), List.of(), Granularity.WEEK, ZONE, null, null))
                .isEmpty();
    }

    @Test
    void usesTheConfiguredTimeZoneForPeriodBoundaries() {
        // 2026-11-22T23:30Z már hétfő 00:30 Budapesten, ezért a következő hétbe tartozik
        WorkoutRow late = new WorkoutRow(9L, Instant.parse("2026-11-22T23:30:00Z"), null, null);
        List<PeriodSummary> weeks = StatisticsCalculator.summarize(List.of(late), List.of(), List.of(),
                Granularity.WEEK, ZONE, null, null);
        assertThat(weeks.get(0).periodStart()).isEqualTo(LocalDate.of(2026, 11, 23));
    }

    // ---- volumen ----

    @Test
    void bodyweightVolumeIncludesBodyWeightMeasuredOnOrBeforeTheWorkout() {
        List<BodyWeightPoint> body = List.of(bw("2026-11-01", "80"), bw("2026-11-17", "78"));
        SetRecord pullUps = bodyweight(3, "2026-11-18T07:00:00", null, 8);

        assertThat(StatisticsCalculator.setVolume(pullUps, body, ZONE)).isEqualByComparingTo("624");   // 78 × 8
        SetRecord weighted = bodyweight(3, "2026-11-18T07:00:00", "10", 8);
        assertThat(StatisticsCalculator.setVolume(weighted, body, ZONE)).isEqualByComparingTo("704");  // (78 + 10) × 8
        SetRecord early = bodyweight(3, "2026-10-01T07:00:00", null, 8);
        assertThat(StatisticsCalculator.setVolume(early, body, ZONE)).isEqualByComparingTo("0");        // még nincs mérés
    }

    @Test
    void cardioSetHasNoVolume() {
        assertThat(StatisticsCalculator.setVolume(cardio(2, "2026-11-15T10:00:00", CardioType.RUNNING, 1800, 5000),
                List.of(), ZONE)).isEqualByComparingTo("0");
    }

    // ---- fejlődési görbe ----

    @Test
    void progressForWeightExerciseHasBestWeightEstimated1RmVolumeAndRelativeStrength() {
        List<SetRecord> sets = List.of(
                strength(10, "2026-11-02T18:00:00", "100", 5),
                strength(10, "2026-11-02T18:00:00", "105", 3),
                strength(10, "2026-11-02T18:00:00", "80", 12),   // 12 ismétlés: nem megbízható 1RM-hez
                strength(11, "2026-11-09T18:00:00", "110", 5));
        List<BodyWeightPoint> body = List.of(bw("2026-11-01", "80"), bw("2026-11-08", "78"));

        List<ExerciseProgressPoint> points = StatisticsCalculator.progress(sets, body, ZONE);

        assertThat(points).hasSize(2);
        ExerciseProgressPoint first = points.get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(first.bestWeightKg()).isEqualByComparingTo("105");
        assertThat(first.estimated1Rm()).isEqualByComparingTo("116.7");            // 100×5 a legjobb (105×3 = 115,5)
        assertThat(first.volumeKg()).isEqualByComparingTo("1775.0");               // 500 + 315 + 960
        assertThat(first.relativeStrength()).isEqualByComparingTo("1.46");         // 116,7 / 80
        assertThat(first.paceMinPerKm()).isNull();
        assertThat(first.speedKmh()).isNull();

        ExerciseProgressPoint second = points.get(1);
        assertThat(second.estimated1Rm()).isEqualByComparingTo("128.3");
        assertThat(second.relativeStrength()).isEqualByComparingTo("1.64");        // 128,3 / 78
    }

    @Test
    void relativeStrengthIsNullWithoutBodyWeight() {
        List<ExerciseProgressPoint> points = StatisticsCalculator.progress(
                List.of(strength(10, "2026-11-02T18:00:00", "100", 5)), List.of(), ZONE);
        assertThat(points.get(0).estimated1Rm()).isNotNull();
        assertThat(points.get(0).relativeStrength()).isNull();
    }

    @Test
    void progressOnlyUsesSetsWithMoreThanTenRepsForVolumeNotFor1Rm() {
        List<ExerciseProgressPoint> points = StatisticsCalculator.progress(
                List.of(strength(10, "2026-11-02T18:00:00", "60", 15)), List.of(), ZONE);
        assertThat(points.get(0).estimated1Rm()).isNull();
        assertThat(points.get(0).volumeKg()).isEqualByComparingTo("900.0");
    }

    @Test
    void runningHasPaceCyclingHasSpeedOtherCardioHasNeither() {
        ExerciseProgressPoint run = StatisticsCalculator.progress(List.of(
                cardio(20, "2026-11-03T07:00:00", CardioType.RUNNING, 1500, 5000),
                cardio(20, "2026-11-03T07:00:00", CardioType.RUNNING, 1500, 5000)), List.of(), ZONE).get(0);
        assertThat(run.paceMinPerKm()).isEqualByComparingTo("5.00");               // 3000 s / 10 km
        assertThat(run.speedKmh()).isNull();
        assertThat(run.estimated1Rm()).isNull();
        assertThat(run.volumeKg()).isEqualByComparingTo("0");

        ExerciseProgressPoint bike = StatisticsCalculator.progress(List.of(
                cardio(21, "2026-11-04T07:00:00", CardioType.CYCLING, 3600, 30000)), List.of(), ZONE).get(0);
        assertThat(bike.speedKmh()).isEqualByComparingTo("30.0");
        assertThat(bike.paceMinPerKm()).isNull();

        ExerciseProgressPoint swim = StatisticsCalculator.progress(List.of(
                cardio(22, "2026-11-05T07:00:00", CardioType.OTHER, 1800, 1000)), List.of(), ZONE).get(0);
        assertThat(swim.paceMinPerKm()).isNull();
        assertThat(swim.speedKmh()).isNull();
    }

    @Test
    void bodyweightExerciseHasNo1RmButHasBestExternalLoadAndVolume() {
        List<ExerciseProgressPoint> points = StatisticsCalculator.progress(
                List.of(bodyweight(30, "2026-11-18T07:00:00", "10", 8)), List.of(bw("2026-11-17", "78")), ZONE);
        assertThat(points.get(0).bestWeightKg()).isEqualByComparingTo("10");
        assertThat(points.get(0).estimated1Rm()).isNull();
        assertThat(points.get(0).volumeKg()).isEqualByComparingTo("704.0");
    }

    // ---- edzéscélok ----

    static GoalSnapshot goal(GoalType type, Long exerciseId, String target, String createdOn) {
        return new GoalSnapshot(1L, type, exerciseId, kg(target), LocalDate.parse(createdOn));
    }

    GoalProgress progressOf(GoalSnapshot goal, List<BodyWeightPoint> body, List<SetRecord> sets) {
        return StatisticsCalculator.goalProgress(List.of(goal), body, sets, ZONE).get(0);
    }

    @Test
    void weightLossGoalProgress() {
        List<BodyWeightPoint> body = List.of(bw("2026-11-01", "90"), bw("2026-11-10", "86"), bw("2026-11-20", "85"));
        GoalProgress progress = progressOf(goal(GoalType.WEIGHT_LOSS, null, "80", "2026-11-05"), body, List.of());

        assertThat(progress.start()).isEqualByComparingTo("90");     // a cél létrehozásakor érvényes mérés
        assertThat(progress.current()).isEqualByComparingTo("85");
        assertThat(progress.percent()).isEqualByComparingTo("50.0");
    }

    @Test
    void muscleGainGoalProgress() {
        List<BodyWeightPoint> body = List.of(bw("2026-11-01", "80"), bw("2026-11-20", "82"));
        GoalProgress progress = progressOf(goal(GoalType.MUSCLE_GAIN, null, "85", "2026-11-01"), body, List.of());
        assertThat(progress.percent()).isEqualByComparingTo("40.0");
    }

    @Test
    void goalProgressIsClampedBetweenZeroAndHundred() {
        List<BodyWeightPoint> wrongWay = List.of(bw("2026-11-01", "90"), bw("2026-11-20", "92"));
        assertThat(progressOf(goal(GoalType.WEIGHT_LOSS, null, "80", "2026-11-01"), wrongWay, List.of()).percent())
                .isEqualByComparingTo("0");

        List<BodyWeightPoint> overshoot = List.of(bw("2026-11-01", "90"), bw("2026-11-20", "75"));
        assertThat(progressOf(goal(GoalType.WEIGHT_LOSS, null, "80", "2026-11-01"), overshoot, List.of()).percent())
                .isEqualByComparingTo("100");
    }

    @Test
    void goalStartsFromEarliestMeasurementIfNoneBeforeCreation() {
        List<BodyWeightPoint> body = List.of(bw("2026-11-10", "90"), bw("2026-11-20", "85"));
        GoalProgress progress = progressOf(goal(GoalType.WEIGHT_LOSS, null, "80", "2026-11-01"), body, List.of());
        assertThat(progress.start()).isEqualByComparingTo("90");
    }

    @Test
    void bodyWeightGoalWithoutMeasurementsHasNoPercent() {
        GoalProgress progress = progressOf(goal(GoalType.WEIGHT_LOSS, null, "80", "2026-11-01"), List.of(), List.of());
        assertThat(progress.percent()).isNull();
        assertThat(progress.current()).isNull();
    }

    @Test
    void strengthGoalUsesHeaviestWeightLifted() {
        List<SetRecord> sets = List.of(strength(1, "2026-11-02T18:00:00", "90", 5), strength(2, "2026-11-09T18:00:00", "80", 5));
        assertThat(progressOf(goal(GoalType.STRENGTH, 1L, "100", "2026-11-01"), List.of(), sets).percent())
                .isEqualByComparingTo("90.0");
        assertThat(progressOf(goal(GoalType.STRENGTH, 1L, "80", "2026-11-01"), List.of(), sets).percent())
                .isEqualByComparingTo("100");       // túlteljesítve, 100-ra vágva
        assertThat(progressOf(goal(GoalType.STRENGTH, null, "100", "2026-11-01"), List.of(), sets).percent()).isNull();
    }

    // ---- állóképességi célok ----

    static GoalSnapshot endurance(EnduranceMetric metric, String target, Integer distanceM, String createdOn) {
        return new GoalSnapshot(1L, GoalType.ENDURANCE, 2L, kg(target), LocalDate.parse(createdOn), metric, distanceM);
    }

    @Test
    void distanceGoalUsesTheLongestSingleDistance() {
        List<SetRecord> sets = List.of(cardio(1, "2026-11-02T18:00:00", CardioType.RUNNING, 1800, 5000),
                cardio(2, "2026-11-09T18:00:00", CardioType.RUNNING, 3000, 8000),
                strength(3, "2026-11-09T19:00:00", "100", 5));                    // másik gyakorlat
        GoalProgress progress = progressOf(endurance(EnduranceMetric.DISTANCE, "10", null, "2026-11-01"), List.of(), sets);
        assertThat(progress.current()).isEqualByComparingTo("8");
        assertThat(progress.percent()).isEqualByComparingTo("80.0");
    }

    @Test
    void durationGoalUsesTheLongestSingleDurationInMinutes() {
        List<SetRecord> sets = List.of(cardio(1, "2026-11-02T18:00:00", CardioType.OTHER, 1800, 1000),
                cardio(2, "2026-11-09T18:00:00", CardioType.OTHER, 2700, 1500));
        GoalProgress progress = progressOf(endurance(EnduranceMetric.DURATION, "60", null, "2026-11-01"), List.of(), sets);
        assertThat(progress.current()).isEqualByComparingTo("45");
        assertThat(progress.percent()).isEqualByComparingTo("75.0");
    }

    @Test
    void timeForDistanceGoalScalesLongerRunsAndMeasuresFromTheTimeWhenTheGoalWasSet() {
        List<SetRecord> sets = List.of(
                cardio(1, "2026-11-02T18:00:00", CardioType.RUNNING, 1800, 5000),    // a cél előtt: 5 km 30:00
                cardio(2, "2026-11-12T18:00:00", CardioType.RUNNING, 1716, 5200),    // 5,2 km 28:36 = 5 km 27:30
                cardio(3, "2026-11-14T18:00:00", CardioType.RUNNING, 900, 3000));    // rövidebb a céltávnál: nem számít
        GoalProgress progress = progressOf(
                endurance(EnduranceMetric.TIME_FOR_DISTANCE, "25", 5000, "2026-11-10"), List.of(), sets);
        assertThat(progress.start()).isEqualByComparingTo("30");
        assertThat(progress.current()).isEqualByComparingTo("27.5");
        assertThat(progress.percent()).isEqualByComparingTo("50.0");                 // 30:00 → 27:30 a 25:00 felé
    }

    @Test
    void timeForDistanceGoalStartsFromTheFirstAttemptIfThereWasNoneBefore() {
        List<SetRecord> sets = List.of(cardio(1, "2026-11-12T18:00:00", CardioType.RUNNING, 1800, 5000),
                cardio(2, "2026-11-20T18:00:00", CardioType.RUNNING, 1650, 5000));
        GoalProgress progress = progressOf(
                endurance(EnduranceMetric.TIME_FOR_DISTANCE, "25", 5000, "2026-11-10"), List.of(), sets);
        assertThat(progress.start()).isEqualByComparingTo("30");
        assertThat(progress.current()).isEqualByComparingTo("27.5");
        assertThat(progress.percent()).isEqualByComparingTo("50.0");
    }

    @Test
    void timeForDistanceGoalIsReachedWhenAnyLongEnoughRunIsFastEnough() {
        List<SetRecord> sets = List.of(cardio(1, "2026-11-12T18:00:00", CardioType.RUNNING, 2880, 10000));  // 10 km 48:00
        assertThat(progressOf(endurance(EnduranceMetric.TIME_FOR_DISTANCE, "25", 5000, "2026-11-10"), List.of(), sets)
                .percent()).isEqualByComparingTo("100");
    }

    @Test
    void enduranceGoalWithoutDataOrExerciseHasNoPercent() {
        List<SetRecord> shortRun = List.of(cardio(1, "2026-11-12T18:00:00", CardioType.RUNNING, 900, 3000));
        assertThat(progressOf(endurance(EnduranceMetric.TIME_FOR_DISTANCE, "25", 5000, "2026-11-10"), List.of(), shortRun)
                .percent()).isNull();
        assertThat(progressOf(endurance(EnduranceMetric.DISTANCE, "10", null, "2026-11-10"), List.of(), List.of())
                .percent()).isNull();
        GoalSnapshot noExercise = new GoalSnapshot(1L, GoalType.ENDURANCE, null, kg("10"), LocalDate.parse("2026-11-10"),
                EnduranceMetric.DISTANCE, null);
        assertThat(progressOf(noExercise, List.of(), shortRun).percent()).isNull();
    }
}

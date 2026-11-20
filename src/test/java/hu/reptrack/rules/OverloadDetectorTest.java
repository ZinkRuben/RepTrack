package hu.reptrack.rules;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.MuscleSetCount;
import hu.reptrack.dto.OverloadRule;
import hu.reptrack.dto.WorkoutRow;
import hu.reptrack.rules.OverloadDetector.Finding;
import hu.reptrack.rules.OverloadDetector.Thresholds;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverloadDetectorTest {

    static final ZoneId ZONE = ZoneId.of("Europe/Budapest");
    static final Thresholds DEFAULT = new Thresholds(new BigDecimal("1.30"), 6, 8);

    static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant();
    }

    /** Lezárt edzés adott kezdéssel, percben megadott hosszal és fáradtsággal. */
    static WorkoutRow workout(long id, String start, int minutes, int fatigue) {
        Instant begin = at(start);
        return new WorkoutRow(id, begin, begin.plusSeconds(minutes * 60L), (short) fatigue);
    }

    static WorkoutRow open(long id, String start) {
        return new WorkoutRow(id, at(start), null, null);
    }

    static MuscleSetCount sets(long workoutId, String start, MuscleGroup muscle, int count) {
        return new MuscleSetCount(workoutId, at(start), muscle, (long) count);
    }

    static List<Finding> detect(List<WorkoutRow> workouts, List<MuscleSetCount> muscleSets, Thresholds thresholds) {
        return OverloadDetector.detect(workouts, muscleSets, thresholds, ZONE);
    }

    static List<Finding> of(List<Finding> findings, OverloadRule rule) {
        return findings.stream().filter(f -> f.rule() == rule).toList();
    }

    // ================= heti terhelés =================

    /** Négy egyforma hét (hétfőnként 100 perc × 6 = 600), mint előzmény: okt. 19., okt. 26., nov. 2., nov. 9. */
    static List<WorkoutRow> steadyHistory() {
        List<WorkoutRow> list = new ArrayList<>();
        list.add(workout(1, "2026-10-19T18:00:00", 100, 6));
        list.add(workout(2, "2026-10-26T18:00:00", 100, 6));
        list.add(workout(3, "2026-11-02T18:00:00", 100, 6));
        list.add(workout(4, "2026-11-09T18:00:00", 100, 6));
        return list;
    }

    @Test
    void weeklyLoadAboveThirtyPercentOfAverageWarnsAtTheWorkoutThatCrossedTheLimit() {
        List<WorkoutRow> all = steadyHistory();
        all.add(workout(100, "2026-11-16T18:00:00", 50, 6));      // 300
        all.add(workout(101, "2026-11-18T18:00:00", 100, 6));     // +600 = 900 > 780

        List<Finding> found = of(detect(all, List.of(), DEFAULT), OverloadRule.WEEKLY_LOAD);

        assertThat(found).hasSize(1);
        Finding f = found.get(0);
        assertThat(f.key()).isEqualTo("week:2026-11-16");
        assertThat(f.workoutId()).isEqualTo(101L);                       // ez lépte át a 780-at
        assertThat(f.eventDate()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(f.message()).contains("900").contains("600").contains("130%");
    }

    @Test
    void loadExactlyAtTheLimitDoesNotWarn() {
        List<WorkoutRow> all = steadyHistory();
        all.add(workout(100, "2026-11-16T18:00:00", 130, 6));     // 780 = 130% × 600, nem több
        assertThat(of(detect(all, List.of(), DEFAULT), OverloadRule.WEEKLY_LOAD)).isEmpty();
    }

    @Test
    void steadyTrainingNeverWarns() {
        List<WorkoutRow> all = steadyHistory();
        all.add(workout(100, "2026-11-16T18:00:00", 100, 6));
        assertThat(of(detect(all, List.of(), DEFAULT), OverloadRule.WEEKLY_LOAD)).isEmpty();
    }

    @Test
    void fewerThanThreeHistoryWeeksNeverWarns() {
        // az első edzés hete nov. 2.: nov. 16-nak csak 2 előzmény hete van (nov. 9., nov. 2.)
        List<WorkoutRow> all = new ArrayList<>(List.of(
                workout(3, "2026-11-02T18:00:00", 100, 6),
                workout(4, "2026-11-09T18:00:00", 100, 6),
                workout(100, "2026-11-16T18:00:00", 300, 10)));          // 3000, rengeteg
        assertThat(of(detect(all, List.of(), DEFAULT), OverloadRule.WEEKLY_LOAD)).isEmpty();
    }

    @Test
    void restWeekAfterStartingCountsAsZeroInTheAverage() {
        // okt. 19., nov. 2., nov. 9. edzett, okt. 26. kimaradt: átlag = 1800 / 4 = 450, határ = 585
        List<WorkoutRow> all = new ArrayList<>(List.of(
                workout(1, "2026-10-19T18:00:00", 100, 6),
                workout(3, "2026-11-02T18:00:00", 100, 6),
                workout(4, "2026-11-09T18:00:00", 100, 6),
                workout(100, "2026-11-16T18:00:00", 100, 6)));           // 600 > 585
        List<Finding> found = of(detect(all, List.of(), DEFAULT), OverloadRule.WEEKLY_LOAD);
        // a pihenőhét utáni visszatérés (nov. 9.: 1200 / 3 = 400 → határ 520) is jelez, és a nov. 16-i hét is
        assertThat(found).extracting(Finding::key).containsExactly("week:2026-11-09", "week:2026-11-16");
    }

    @Test
    void weeksBeforeTheFirstWorkoutAreNotHistory() {
        // egyforma edzések az első naptól: a 4. héten sem kell hamis figyelmeztetés a „hiányzó” korábbi hetek miatt
        List<WorkoutRow> all = steadyHistory();
        assertThat(of(detect(all, List.of(), DEFAULT), OverloadRule.WEEKLY_LOAD)).isEmpty();
    }

    @Test
    void openAndFatiguelessWorkoutsAreIgnoredForLoad() {
        List<WorkoutRow> all = steadyHistory();
        all.add(open(100, "2026-11-16T18:00:00"));
        all.add(new WorkoutRow(101L, at("2026-11-17T18:00:00"), at("2026-11-17T20:00:00"), null));   // nincs fáradtság
        assertThat(detect(all, List.of(), DEFAULT)).isEmpty();
    }

    @Test
    void customRatioIsRespected() {
        List<WorkoutRow> all = steadyHistory();
        all.add(workout(100, "2026-11-16T18:00:00", 140, 6));     // 840: 130% fölött, 150% alatt
        assertThat(of(detect(all, List.of(), DEFAULT), OverloadRule.WEEKLY_LOAD)).hasSize(1);
        assertThat(of(detect(all, List.of(), new Thresholds(new BigDecimal("1.50"), 6, 8)), OverloadRule.WEEKLY_LOAD)).isEmpty();
    }

    @Test
    void sundayNightWorkoutBelongsToTheWeekItStartedIn() {
        List<WorkoutRow> all = steadyHistory();
        all.add(workout(100, "2026-11-15T23:30:00", 130, 6));     // vasárnap este indul: még a nov. 9-i hét (600 + 780)
        List<Finding> found = of(detect(all, List.of(), DEFAULT), OverloadRule.WEEKLY_LOAD);
        assertThat(found).extracting(Finding::key).containsExactly("week:2026-11-09");   // 1380 > 1,3 × 600 = 780
    }

    // ================= izomcsoport három egymást követő napon =================

    @Test
    void sameMuscleThreeDaysInARowWarnsOnTheThirdDay() {
        List<MuscleSetCount> counts = List.of(
                sets(1, "2026-11-16T18:00:00", MuscleGroup.QUADS, 6),
                sets(2, "2026-11-17T18:00:00", MuscleGroup.QUADS, 7),
                sets(3, "2026-11-18T18:00:00", MuscleGroup.QUADS, 6));

        List<Finding> found = of(detect(List.of(), counts, DEFAULT), OverloadRule.MUSCLE_SETS);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).key()).isEqualTo("muscle:QUADS:2026-11-18");
        assertThat(found.get(0).eventDate()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(found.get(0).workoutId()).isEqualTo(3L);
        assertThat(found.get(0).message()).contains("combfeszítő").contains("6 szettet");
    }

    @Test
    void twoDaysAreNotEnough() {
        assertThat(detect(List.of(), List.of(
                sets(1, "2026-11-16T18:00:00", MuscleGroup.QUADS, 8),
                sets(2, "2026-11-17T18:00:00", MuscleGroup.QUADS, 8)), DEFAULT)).isEmpty();
    }

    @Test
    void dayBelowThresholdBreaksTheStreak() {
        assertThat(detect(List.of(), List.of(
                sets(1, "2026-11-16T18:00:00", MuscleGroup.QUADS, 6),
                sets(2, "2026-11-17T18:00:00", MuscleGroup.QUADS, 5),       // 5 < 6
                sets(3, "2026-11-18T18:00:00", MuscleGroup.QUADS, 6)), DEFAULT)).isEmpty();
    }

    @Test
    void restDayBreaksTheStreak() {
        assertThat(detect(List.of(), List.of(
                sets(1, "2026-11-16T18:00:00", MuscleGroup.QUADS, 6),
                sets(2, "2026-11-17T18:00:00", MuscleGroup.QUADS, 6),
                sets(3, "2026-11-19T18:00:00", MuscleGroup.QUADS, 6)), DEFAULT)).isEmpty();   // 18-án pihenő
    }

    @Test
    void setsOfTwoWorkoutsOnTheSameDayAreAdded() {
        List<Finding> found = of(detect(List.of(), List.of(
                sets(1, "2026-11-16T08:00:00", MuscleGroup.CHEST, 3),
                sets(2, "2026-11-16T18:00:00", MuscleGroup.CHEST, 3),       // 3 + 3 = 6
                sets(3, "2026-11-17T18:00:00", MuscleGroup.CHEST, 6),
                sets(4, "2026-11-18T08:00:00", MuscleGroup.CHEST, 4),
                sets(5, "2026-11-18T18:00:00", MuscleGroup.CHEST, 2)), DEFAULT), OverloadRule.MUSCLE_SETS);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).workoutId()).isEqualTo(5L);                  // a nap utolsó edzése
    }

    @Test
    void longStreakWarnsOnlyOnce() {
        List<MuscleSetCount> counts = new ArrayList<>();
        for (int day = 16; day <= 20; day++) {
            counts.add(sets(day, "2026-11-" + day + "T18:00:00", MuscleGroup.BACK, 6));
        }
        List<Finding> found = of(detect(List.of(), counts, DEFAULT), OverloadRule.MUSCLE_SETS);
        assertThat(found).extracting(Finding::key).containsExactly("muscle:BACK:2026-11-18");
    }

    @Test
    void newStreakAfterABreakWarnsAgain() {
        List<MuscleSetCount> counts = new ArrayList<>();
        for (int day : new int[]{2, 3, 4, 6, 7, 8}) {                         // 5-én pihenő
            counts.add(sets(day, "2026-11-0" + day + "T18:00:00", MuscleGroup.BACK, 6));
        }
        assertThat(of(detect(List.of(), counts, DEFAULT), OverloadRule.MUSCLE_SETS))
                .extracting(Finding::key).containsExactly("muscle:BACK:2026-11-04", "muscle:BACK:2026-11-08");
    }

    @Test
    void musclesAreIndependentAndThresholdIsConfigurable() {
        List<MuscleSetCount> counts = List.of(
                sets(1, "2026-11-16T18:00:00", MuscleGroup.CHEST, 6),
                sets(2, "2026-11-17T18:00:00", MuscleGroup.BACK, 6),         // nem ugyanaz az izomcsoport
                sets(3, "2026-11-18T18:00:00", MuscleGroup.CHEST, 6));
        assertThat(detect(List.of(), counts, DEFAULT)).isEmpty();

        List<MuscleSetCount> three = List.of(
                sets(1, "2026-11-16T18:00:00", MuscleGroup.CHEST, 4),
                sets(2, "2026-11-17T18:00:00", MuscleGroup.CHEST, 4),
                sets(3, "2026-11-18T18:00:00", MuscleGroup.CHEST, 4));
        assertThat(detect(List.of(), three, DEFAULT)).isEmpty();
        assertThat(detect(List.of(), three, new Thresholds(new BigDecimal("1.30"), 4, 8))).hasSize(1);
    }

    // ================= magas fáradtság =================

    @Test
    void threeConsecutiveHighFatigueWorkoutsWarnAtTheThird() {
        List<WorkoutRow> all = List.of(
                workout(1, "2026-11-16T18:00:00", 30, 8),
                workout(2, "2026-11-17T18:00:00", 30, 9),
                workout(3, "2026-11-18T18:00:00", 30, 10));

        List<Finding> found = of(detect(all, List.of(), DEFAULT), OverloadRule.HIGH_FATIGUE);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).key()).isEqualTo("workout:3");
        assertThat(found.get(0).workoutId()).isEqualTo(3L);
        assertThat(found.get(0).message()).contains("legalább 8");
    }

    @Test
    void aLowerFatigueInTheMiddleBreaksTheSequence() {
        assertThat(of(detect(List.of(
                workout(1, "2026-11-16T18:00:00", 30, 9),
                workout(2, "2026-11-17T18:00:00", 30, 7),
                workout(3, "2026-11-18T18:00:00", 30, 9)), List.of(), DEFAULT), OverloadRule.HIGH_FATIGUE)).isEmpty();
    }

    @Test
    void sequenceFollowsChronologicalOrderNotInputOrder() {
        List<WorkoutRow> all = new ArrayList<>(List.of(
                workout(3, "2026-11-18T18:00:00", 30, 9),
                workout(1, "2026-11-16T18:00:00", 30, 9),
                workout(4, "2026-11-19T18:00:00", 30, 5),
                workout(2, "2026-11-17T18:00:00", 30, 9)));
        assertThat(of(detect(all, List.of(), DEFAULT), OverloadRule.HIGH_FATIGUE))
                .extracting(Finding::key).containsExactly("workout:3");
    }

    @Test
    void longHighFatigueSequenceWarnsOnlyOnceAndOpenWorkoutsAreSkipped() {
        List<WorkoutRow> all = List.of(
                workout(1, "2026-11-16T18:00:00", 30, 9),
                open(2, "2026-11-17T08:00:00"),                                  // nyitott: nem része a sorozatnak
                workout(3, "2026-11-17T18:00:00", 30, 9),
                workout(4, "2026-11-18T18:00:00", 30, 9),
                workout(5, "2026-11-19T18:00:00", 30, 9));
        assertThat(of(detect(all, List.of(), DEFAULT), OverloadRule.HIGH_FATIGUE))
                .extracting(Finding::key).containsExactly("workout:4");
    }

    @Test
    void fatigueThresholdIsConfigurable() {
        List<WorkoutRow> all = List.of(
                workout(1, "2026-11-16T18:00:00", 30, 9),
                workout(2, "2026-11-17T18:00:00", 30, 9),
                workout(3, "2026-11-18T18:00:00", 30, 9));
        assertThat(of(detect(all, List.of(), new Thresholds(new BigDecimal("1.30"), 6, 10)), OverloadRule.HIGH_FATIGUE)).isEmpty();
        assertThat(of(detect(all, List.of(), new Thresholds(new BigDecimal("1.30"), 6, 9)), OverloadRule.HIGH_FATIGUE)).hasSize(1);
    }

    @Test
    void emptyInputHasNoFindings() {
        assertThat(detect(List.of(), List.of(), DEFAULT)).isEmpty();
    }
}

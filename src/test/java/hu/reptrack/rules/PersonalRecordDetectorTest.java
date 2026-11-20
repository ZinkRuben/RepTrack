package hu.reptrack.rules;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.DistanceBand;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.RecordSet;
import hu.reptrack.dto.RecordType;
import hu.reptrack.rules.PersonalRecordDetector.Found;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalRecordDetectorTest {

    static final ZoneId ZONE = ZoneId.of("Europe/Budapest");
    static long nextSetId = 1;

    static Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant();
    }

    static RecordSet lift(long workoutId, String start, String kg, int reps) {
        return new RecordSet(nextSetId++, workoutId, at(start), ExerciseCategory.WEIGHTS, null, new BigDecimal(kg),
                (short) reps, null, null);
    }

    static RecordSet run(long workoutId, String start, int durationSec, int distanceM) {
        return new RecordSet(nextSetId++, workoutId, at(start), ExerciseCategory.CARDIO, CardioType.RUNNING, null, null,
                durationSec, distanceM);
    }

    static List<Found> of(List<Found> all, RecordType type) {
        return all.stream().filter(f -> f.type() == type).toList();
    }

    // ---- alapszabályok ----

    @Test
    void firstResultIsNotARecord() {
        assertThat(PersonalRecordDetector.detect(List.of(lift(1, "2026-11-02T18:00:00", "100", 5)), ZONE)).isEmpty();
    }

    @Test
    void heavierSetLaterIsARecordInEveryApplicableType() {
        List<Found> found = PersonalRecordDetector.detect(List.of(
                lift(1, "2026-11-02T18:00:00", "100", 5),
                lift(2, "2026-11-09T18:00:00", "105", 5)), ZONE);

        assertThat(found).extracting(Found::type).containsExactlyInAnyOrder(
                RecordType.MAX_WEIGHT_AT_REPS, RecordType.ESTIMATED_1RM, RecordType.SET_VOLUME);
        Found weight = of(found, RecordType.MAX_WEIGHT_AT_REPS).get(0);
        assertThat(weight.reps()).isEqualTo((short) 5);
        assertThat(weight.value()).isEqualByComparingTo("105");
        assertThat(weight.achievedOn()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(weight.workoutId()).isEqualTo(2L);
        assertThat(of(found, RecordType.ESTIMATED_1RM).get(0).value()).isEqualByComparingTo("122.5");   // 105 × (1 + 5/30)
        assertThat(of(found, RecordType.SET_VOLUME).get(0).value()).isEqualByComparingTo("525");
    }

    @Test
    void equalResultIsNotARecord() {
        assertThat(PersonalRecordDetector.detect(List.of(
                lift(1, "2026-11-02T18:00:00", "100", 5),
                lift(2, "2026-11-09T18:00:00", "100", 5)), ZONE)).isEmpty();
    }

    @Test
    void lighterResultIsNotARecord() {
        assertThat(PersonalRecordDetector.detect(List.of(
                lift(1, "2026-11-02T18:00:00", "100", 5),
                lift(2, "2026-11-09T18:00:00", "95", 5)), ZONE)).isEmpty();
    }

    @Test
    void weightRecordsAreTrackedSeparatelyPerRepCount() {
        List<Found> found = PersonalRecordDetector.detect(List.of(
                lift(1, "2026-11-02T18:00:00", "100", 5),
                lift(2, "2026-11-09T18:00:00", "80", 8),       // új ismétlésszám: első eredmény ott, nem PR
                lift(3, "2026-11-16T18:00:00", "85", 8)), ZONE);

        List<Found> weightRecords = of(found, RecordType.MAX_WEIGHT_AT_REPS);
        assertThat(weightRecords).hasSize(1);
        assertThat(weightRecords.get(0).reps()).isEqualTo((short) 8);
        assertThat(weightRecords.get(0).value()).isEqualByComparingTo("85");
    }

    @Test
    void onlyTheBestSetOfAWorkoutCountsAndTheEarlierWinsTies() {
        List<Found> found = PersonalRecordDetector.detect(List.of(
                lift(1, "2026-11-02T18:00:00", "100", 5),
                lift(2, "2026-11-09T18:00:00", "105", 5),
                lift(2, "2026-11-09T18:00:00", "110", 5),
                lift(2, "2026-11-09T18:00:00", "110", 5)), ZONE);

        List<Found> weightRecords = of(found, RecordType.MAX_WEIGHT_AT_REPS);
        assertThat(weightRecords).hasSize(1);                                        // egy PR az edzésre, nem három
        assertThat(weightRecords.get(0).value()).isEqualByComparingTo("110");
    }

    @Test
    void estimatedOneRepMaxIgnoresSetsOverTenReps() {
        List<Found> found = PersonalRecordDetector.detect(List.of(
                lift(1, "2026-11-02T18:00:00", "100", 5),
                lift(2, "2026-11-09T18:00:00", "90", 12)), ZONE);

        assertThat(of(found, RecordType.ESTIMATED_1RM)).isEmpty();          // 12 ismétlésből nincs 1RM
        assertThat(of(found, RecordType.SET_VOLUME)).hasSize(1);            // a volumen viszont rekord: 1080 > 500
    }

    // ---- időrend ----

    @Test
    void resultDoesNotDependOnInputOrder() {
        List<RecordSet> sets = new ArrayList<>(List.of(
                lift(1, "2026-11-02T18:00:00", "100", 5),
                lift(2, "2026-11-09T18:00:00", "105", 5),
                lift(3, "2026-11-16T18:00:00", "110", 5)));
        List<Found> expected = PersonalRecordDetector.detect(sets, ZONE);
        Collections.reverse(sets);

        assertThat(PersonalRecordDetector.detect(sets, ZONE)).isEqualTo(expected);
        assertThat(of(expected, RecordType.MAX_WEIGHT_AT_REPS)).extracting(Found::workoutId).containsExactly(2L, 3L);
    }

    @Test
    void backdatedHeavySetTakesOverTheRecordFromLaterOnes() {
        // a 110 kg korábban 105-nél volt PR, de egy utólag felvett, korábbi 120 kg elveszi a rekordot
        List<Found> found = PersonalRecordDetector.detect(List.of(
                lift(1, "2026-11-02T18:00:00", "100", 5),
                lift(2, "2026-11-16T18:00:00", "110", 5),
                lift(3, "2026-11-09T18:00:00", "120", 5)), ZONE);

        List<Found> weightRecords = of(found, RecordType.MAX_WEIGHT_AT_REPS);
        assertThat(weightRecords).extracting(Found::workoutId).containsExactly(3L);   // a 110 már nem PR
    }

    @Test
    void achievedOnUsesTheConfiguredTimeZone() {
        RecordSet base = lift(1, "2026-11-02T18:00:00", "100", 5);
        RecordSet late = new RecordSet(nextSetId++, 2L, Instant.parse("2026-11-09T23:30:00Z"), ExerciseCategory.WEIGHTS, null,
                new BigDecimal("105"), (short) 5, null, null);

        Found found = PersonalRecordDetector.detect(List.of(base, late), ZONE).get(0);
        assertThat(found.achievedOn()).isEqualTo(LocalDate.of(2026, 11, 10));    // Budapesten már másnap
    }

    // ---- saját testsúlyos ----

    @Test
    void bodyweightExerciseOnlyCountsExternalLoadForWeightAtReps() {
        RecordSet plain1 = new RecordSet(nextSetId++, 1L, at("2026-11-02T18:00:00"), ExerciseCategory.BODYWEIGHT, null,
                null, (short) 10, null, null);
        RecordSet plain2 = new RecordSet(nextSetId++, 2L, at("2026-11-09T18:00:00"), ExerciseCategory.BODYWEIGHT, null,
                null, (short) 12, null, null);
        assertThat(PersonalRecordDetector.detect(List.of(plain1, plain2), ZONE)).isEmpty();   // külső terhelés nélkül nincs PR

        RecordSet loaded1 = new RecordSet(nextSetId++, 3L, at("2026-11-16T18:00:00"), ExerciseCategory.BODYWEIGHT, null,
                new BigDecimal("10"), (short) 8, null, null);
        RecordSet loaded2 = new RecordSet(nextSetId++, 4L, at("2026-11-23T18:00:00"), ExerciseCategory.BODYWEIGHT, null,
                new BigDecimal("15"), (short) 8, null, null);
        List<Found> found = PersonalRecordDetector.detect(List.of(loaded1, loaded2), ZONE);
        assertThat(found).extracting(Found::type).containsExactly(RecordType.MAX_WEIGHT_AT_REPS);   // nincs 1RM és volumen
    }

    // ---- futás ----

    @Test
    void fasterPaceInTheSameBandIsARecord() {
        List<Found> found = PersonalRecordDetector.detect(List.of(
                run(1, "2026-11-02T07:00:00", 1500, 5000),      // 5:00 /km
                run(2, "2026-11-09T07:00:00", 1450, 5000),      // 4:50 /km: PR
                run(3, "2026-11-16T07:00:00", 1560, 5000)), ZONE);   // 5:12 /km: lassabb

        assertThat(found).hasSize(1);
        Found record = found.get(0);
        assertThat(record.type()).isEqualTo(RecordType.BEST_PACE);
        assertThat(record.band()).isEqualTo(DistanceBand.FIVE_K);
        assertThat(record.value()).isEqualByComparingTo("4.83");
        assertThat(record.reps()).isNull();
    }

    @Test
    void runsAreComparedOnlyWithinTheirDistanceBand() {
        List<Found> found = PersonalRecordDetector.detect(List.of(
                run(1, "2026-11-02T07:00:00", 1500, 5000),      // 5K sáv
                run(2, "2026-11-09T07:00:00", 2800, 10000),     // 10K sáv, első eredmény
                run(3, "2026-11-16T07:00:00", 2700, 10000)), ZONE);   // 10K: 4:30 < 4:40, PR

        assertThat(found).hasSize(1);
        assertThat(found.get(0).band()).isEqualTo(DistanceBand.TEN_K);
    }

    @Test
    void runBelongsToTheLargestBandNotExceedingItsDistance() {
        List<Found> found = PersonalRecordDetector.detect(List.of(
                run(1, "2026-11-02T07:00:00", 1800, 6000),      // 6 km → 5K sáv, 5:00
                run(2, "2026-11-09T07:00:00", 1740, 6000)), ZONE);   // 4:50 → PR az 5K sávban

        assertThat(found).extracting(Found::band).containsExactly(DistanceBand.FIVE_K);
    }

    @Test
    void runShorterThanOneKilometerHasNoBand() {
        assertThat(PersonalRecordDetector.detect(List.of(
                run(1, "2026-11-02T07:00:00", 300, 900),
                run(2, "2026-11-09T07:00:00", 240, 900)), ZONE)).isEmpty();
    }

    @Test
    void cyclingAndOtherCardioHaveNoPaceRecords() {
        RecordSet bike1 = new RecordSet(nextSetId++, 1L, at("2026-11-02T07:00:00"), ExerciseCategory.CARDIO, CardioType.CYCLING,
                null, null, 3600, 30000);
        RecordSet bike2 = new RecordSet(nextSetId++, 2L, at("2026-11-09T07:00:00"), ExerciseCategory.CARDIO, CardioType.CYCLING,
                null, null, 3000, 30000);
        RecordSet swim1 = new RecordSet(nextSetId++, 3L, at("2026-11-16T07:00:00"), ExerciseCategory.CARDIO, CardioType.OTHER,
                null, null, 1800, 1000);
        RecordSet swim2 = new RecordSet(nextSetId++, 4L, at("2026-11-23T07:00:00"), ExerciseCategory.CARDIO, CardioType.OTHER,
                null, null, 1500, 1000);
        assertThat(PersonalRecordDetector.detect(List.of(bike1, bike2, swim1, swim2), ZONE)).isEmpty();
    }

    @Test
    void emptyInputGivesNoRecords() {
        assertThat(PersonalRecordDetector.detect(List.of(), ZONE)).isEmpty();
    }

    // ---- távsáv ----

    @Test
    void distanceBandBoundaries() {
        assertThat(DistanceBand.forDistance(999)).isNull();
        assertThat(DistanceBand.forDistance(1000)).isEqualTo(DistanceBand.ONE_K);
        assertThat(DistanceBand.forDistance(4999)).isEqualTo(DistanceBand.ONE_K);
        assertThat(DistanceBand.forDistance(5000)).isEqualTo(DistanceBand.FIVE_K);
        assertThat(DistanceBand.forDistance(10000)).isEqualTo(DistanceBand.TEN_K);
        assertThat(DistanceBand.forDistance(21097)).isEqualTo(DistanceBand.HALF_MARATHON);
        assertThat(DistanceBand.forDistance(42195)).isEqualTo(DistanceBand.MARATHON);
        assertThat(DistanceBand.forDistance(100_000)).isEqualTo(DistanceBand.MARATHON);
    }
}

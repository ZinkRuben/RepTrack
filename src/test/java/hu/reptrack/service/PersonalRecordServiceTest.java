package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.DistanceBand;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.PersonalRecordView;
import hu.reptrack.dto.RecordType;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.dto.WorkoutView;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** A PR-ek az edzések mentésekor, módosításakor és törlésekor automatikusan újraszámolódnak (WorkoutService bekötés). */
@Transactional
class PersonalRecordServiceTest extends IntegrationTestBase {

    static final ZoneId ZONE = ZoneId.of("Europe/Budapest");

    @Autowired PersonalRecordService personalRecords;
    @Autowired WorkoutService workouts;
    @Autowired UserRepository users;
    @Autowired ExerciseRepository exercises;
    @Autowired EntityManager em;

    User alice;
    User bob;
    Exercise squat;
    Exercise run;

    @BeforeEach
    void setUp() {
        alice = users.save(user("alice@example.com"));
        bob = users.save(user("bob@example.com"));
        squat = exercise("Teszt guggolás", ExerciseCategory.WEIGHTS, null);
        run = exercise("Teszt futás", ExerciseCategory.CARDIO, CardioType.RUNNING);
    }

    private User user(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setDisplayName("Teszt");
        return u;
    }

    private Exercise exercise(String name, ExerciseCategory category, CardioType cardioType) {
        Exercise e = new Exercise();
        e.setName(name);
        e.setCategory(category);
        e.setCardioType(cardioType);
        e.setTracksWeight(category == ExerciseCategory.WEIGHTS);
        e.setTracksReps(category == ExerciseCategory.WEIGHTS);
        e.setTracksDuration(category == ExerciseCategory.CARDIO);
        e.setTracksDistance(category == ExerciseCategory.CARDIO);
        return exercises.save(e);
    }

    private Instant at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant();
    }

    private WorkoutView liftWorkout(User user, String start, String kg, int reps) {
        Instant begin = at(start);
        var set = new WorkoutSetRequest(new BigDecimal(kg), (short) reps, null, null, null, null);
        return workouts.create(user.getId(), new WorkoutRequest("Erő", begin, begin.plusSeconds(3600), (short) 7, null, null,
                List.of(new WorkoutExerciseRequest(squat.getId(), null, List.of(set)))));
    }

    private WorkoutView runWorkout(User user, String start, int durationSec, int distanceM) {
        Instant begin = at(start);
        var set = new WorkoutSetRequest(null, null, durationSec, distanceM, null, null);
        return workouts.create(user.getId(), new WorkoutRequest("Futás", begin, begin.plusSeconds(durationSec + 600L), (short) 6,
                null, null, List.of(new WorkoutExerciseRequest(run.getId(), null, List.of(set)))));
    }

    private WorkoutView replaceWithLift(User user, WorkoutView existing, String kg, int reps) {
        var set = new WorkoutSetRequest(new BigDecimal(kg), (short) reps, null, null, null, null);
        return workouts.update(user.getId(), existing.id(), new WorkoutRequest(existing.title(), existing.startedAt(),
                existing.endedAt(), existing.fatigue(), null, existing.version(),
                List.of(new WorkoutExerciseRequest(squat.getId(), null, List.of(set)))));
    }

    private List<PersonalRecordView> weightRecords(User user) {
        return personalRecords.list(user.getId(), squat.getId()).stream()
                .filter(r -> r.recordType() == RecordType.MAX_WEIGHT_AT_REPS).toList();
    }

    @Test
    void savingAHeavierWorkoutCreatesRecordsAutomatically() {
        liftWorkout(alice, "2026-11-02T18:00:00", "100", 5);
        assertThat(personalRecords.list(alice.getId(), null)).isEmpty();          // az első edzés nem PR

        WorkoutView second = liftWorkout(alice, "2026-11-09T18:00:00", "105", 5);

        List<PersonalRecordView> records = personalRecords.list(alice.getId(), squat.getId());
        assertThat(records).extracting(PersonalRecordView::recordType).containsExactlyInAnyOrder(
                RecordType.MAX_WEIGHT_AT_REPS, RecordType.ESTIMATED_1RM, RecordType.SET_VOLUME);
        assertThat(records).allSatisfy(r -> {
            assertThat(r.exerciseName()).isEqualTo("Teszt guggolás");
            assertThat(r.achievedOn()).isEqualTo(LocalDate.of(2026, 11, 9));
            assertThat(r.workoutId()).isEqualTo(second.id());
        });
        assertThat(personalRecords.achievedIn(alice.getId(), second.id())).hasSize(3);
    }

    @Test
    void recordsAreListedNewestFirst() {
        liftWorkout(alice, "2026-11-02T18:00:00", "100", 5);
        liftWorkout(alice, "2026-11-09T18:00:00", "105", 5);
        liftWorkout(alice, "2026-11-16T18:00:00", "110", 5);

        assertThat(weightRecords(alice)).extracting(PersonalRecordView::achievedOn)
                .containsExactly(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 9));
    }

    @Test
    void editingAWorkoutRecalculatesRecords() {
        liftWorkout(alice, "2026-11-02T18:00:00", "100", 5);
        WorkoutView second = liftWorkout(alice, "2026-11-09T18:00:00", "105", 5);
        assertThat(weightRecords(alice)).hasSize(1);

        replaceWithLift(alice, second, "95", 5);                                   // a második edzés most gyengébb

        assertThat(personalRecords.list(alice.getId(), squat.getId())).isEmpty();
    }

    @Test
    void deletingAWorkoutRecalculatesLaterRecords() {
        WorkoutView first = liftWorkout(alice, "2026-11-02T18:00:00", "100", 5);
        liftWorkout(alice, "2026-11-09T18:00:00", "105", 5);
        liftWorkout(alice, "2026-11-16T18:00:00", "110", 5);
        assertThat(weightRecords(alice)).extracting(r -> r.value().intValue()).containsExactly(110, 105);

        workouts.delete(alice.getId(), first.id());                                // az új alap a 105 kg: a 105 már nem PR

        assertThat(weightRecords(alice)).extracting(r -> r.value().intValue()).containsExactly(110);
    }

    @Test
    void backdatedWorkoutTakesOverTheRecord() {
        liftWorkout(alice, "2026-11-02T18:00:00", "100", 5);
        liftWorkout(alice, "2026-11-16T18:00:00", "110", 5);
        assertThat(weightRecords(alice)).extracting(PersonalRecordView::achievedOn).containsExactly(LocalDate.of(2026, 11, 16));

        liftWorkout(alice, "2026-11-09T18:00:00", "120", 5);                       // korábbi dátumra, nehezebb

        assertThat(weightRecords(alice)).extracting(PersonalRecordView::achievedOn).containsExactly(LocalDate.of(2026, 11, 9));
    }

    @Test
    void openWorkoutsDoNotCount() {
        liftWorkout(alice, "2026-11-02T18:00:00", "100", 5);
        Instant begin = at("2026-11-09T18:00:00");
        var set = new WorkoutSetRequest(new BigDecimal("200"), (short) 5, null, null, null, null);
        workouts.create(alice.getId(), new WorkoutRequest("Nyitott", begin, null, null, null, null,
                List.of(new WorkoutExerciseRequest(squat.getId(), null, List.of(set)))));

        assertThat(personalRecords.list(alice.getId(), null)).isEmpty();
    }

    @Test
    void paceRecordsPerDistanceBand() {
        runWorkout(alice, "2026-11-02T07:00:00", 1500, 5000);
        runWorkout(alice, "2026-11-09T07:00:00", 1450, 5000);

        List<PersonalRecordView> records = personalRecords.list(alice.getId(), run.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).recordType()).isEqualTo(RecordType.BEST_PACE);
        assertThat(records.get(0).distanceBand()).isEqualTo(DistanceBand.FIVE_K);
        assertThat(records.get(0).value()).isEqualByComparingTo("4.83");
    }

    @Test
    void recordsAreOwnPerUser() {
        liftWorkout(alice, "2026-11-02T18:00:00", "100", 5);
        liftWorkout(alice, "2026-11-09T18:00:00", "105", 5);
        liftWorkout(bob, "2026-11-02T18:00:00", "200", 5);

        assertThat(personalRecords.list(bob.getId(), null)).isEmpty();            // Bob első eredménye nem PR
        assertThat(personalRecords.list(alice.getId(), null)).isNotEmpty();
    }

    @Test
    void recalculationIsIdempotent() {
        liftWorkout(alice, "2026-11-02T18:00:00", "100", 5);
        liftWorkout(alice, "2026-11-09T18:00:00", "105", 5);
        List<PersonalRecordView> before = personalRecords.list(alice.getId(), squat.getId());

        personalRecords.recalculate(alice.getId(), squat.getId());
        personalRecords.recalculate(alice.getId(), List.of(squat.getId(), squat.getId()));
        em.flush();
        em.clear();

        List<PersonalRecordView> after = personalRecords.list(alice.getId(), squat.getId());
        assertThat(after).hasSameSizeAs(before);
        assertThat(after).extracting(PersonalRecordView::recordType)
                .containsExactlyInAnyOrderElementsOf(before.stream().map(PersonalRecordView::recordType).toList());
    }

    @Test
    void deletingTheWorkoutThatSetARecordRemovesIt() {
        liftWorkout(alice, "2026-11-02T18:00:00", "100", 5);
        WorkoutView second = liftWorkout(alice, "2026-11-09T18:00:00", "105", 5);
        assertThat(personalRecords.list(alice.getId(), null)).isNotEmpty();

        workouts.delete(alice.getId(), second.id());

        assertThat(personalRecords.list(alice.getId(), null)).isEmpty();
    }
}

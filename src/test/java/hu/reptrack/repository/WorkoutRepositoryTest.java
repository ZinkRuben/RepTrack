package hu.reptrack.repository;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.OverloadWarning;
import hu.reptrack.domain.PersonalRecord;
import hu.reptrack.domain.TemplateExercise;
import hu.reptrack.domain.User;
import hu.reptrack.domain.Workout;
import hu.reptrack.domain.WorkoutExercise;
import hu.reptrack.domain.WorkoutSet;
import hu.reptrack.domain.WorkoutTemplate;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.OverloadRule;
import hu.reptrack.dto.RecordType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class WorkoutRepositoryTest extends IntegrationTestBase {

    @Autowired UserRepository users;
    @Autowired ExerciseRepository exercises;
    @Autowired WorkoutRepository workouts;
    @Autowired WorkoutTemplateRepository templates;
    @Autowired PersonalRecordRepository records;
    @Autowired OverloadWarningRepository warnings;
    @Autowired EntityManager em;

    User owner;
    Exercise squat;

    @BeforeEach
    void setUp() {
        owner = users.save(user("owner@example.com"));
        squat = new Exercise();
        squat.setName("Guggolás");
        squat.setCategory(ExerciseCategory.WEIGHTS);
        squat.setPrimaryMuscle(MuscleGroup.QUADS);
        squat.setSecondaryMuscles(new java.util.LinkedHashSet<>(java.util.List.of(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS)));
        squat.setTracksWeight(true);
        squat.setTracksReps(true);
        squat = exercises.save(squat);
    }

    private User user(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setDisplayName("Teszt");
        return u;
    }

    private WorkoutSet set(int position, String kg, int reps) {
        WorkoutSet s = new WorkoutSet();
        s.setPosition((short) position);
        s.setWeightKg(new BigDecimal(kg));
        s.setReps((short) reps);
        return s;
    }

    private Workout workoutWithTwoSets() {
        Workout w = new Workout();
        w.setUser(owner);
        w.setTitle("Lábnap");
        w.setStartedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        w.setEndedAt(Instant.now());
        w.setFatigue((short) 7);
        WorkoutExercise we = new WorkoutExercise();
        we.setExercise(squat);
        we.setPosition((short) 1);
        we.addSet(set(2, "105.0", 5));
        we.addSet(set(1, "100.0", 5));
        w.addExercise(we);
        return workouts.saveAndFlush(w);
    }

    @Test
    void savesWorkoutGraphThroughCascade() {
        Long id = workoutWithTwoSets().getId();
        em.clear();

        Workout loaded = workouts.findByIdAndUserId(id, owner.getId()).orElseThrow();
        assertThat(loaded.getExercises()).hasSize(1);
        WorkoutExercise we = loaded.getExercises().get(0);
        assertThat(we.getExercise().getName()).isEqualTo("Guggolás");
        assertThat(we.getSets()).extracting(WorkoutSet::getPosition).containsExactly((short) 1, (short) 2);
        assertThat(we.getSets().get(0).getWeightKg()).isEqualByComparingTo("100.0");
        assertThat(loaded.getVersion()).isNotNull();
    }

    @Test
    void otherUserCannotLoadWorkout() {
        Long id = workoutWithTwoSets().getId();
        User other = users.save(user("other@example.com"));

        assertThat(workouts.findByIdAndUserId(id, other.getId())).isEmpty();
        assertThat(workouts.findByUserId(other.getId(), PageRequest.of(0, 10))).isEmpty();
        assertThat(workouts.findByUserId(owner.getId(), PageRequest.of(0, 10))).hasSize(1);
    }

    @Test
    void deletingWorkoutRemovesExercisesAndSets() {
        Workout w = workoutWithTwoSets();
        workouts.delete(w);
        workouts.flush();

        Number sets = (Number) em.createNativeQuery("select count(*) from workout_sets").getSingleResult();
        Number links = (Number) em.createNativeQuery("select count(*) from workout_exercises").getSingleResult();
        assertThat(sets.longValue()).isZero();
        assertThat(links.longValue()).isZero();
    }

    @Test
    void sharedExerciseHasNoOwner() {
        assertThat(squat.isShared()).isTrue();
        squat.setOwner(owner);
        assertThat(squat.isShared()).isFalse();
    }

    @Test
    void savesTemplateWithExercises() {
        WorkoutTemplate t = new WorkoutTemplate();
        t.setUser(owner);
        t.setName("Láb A");
        TemplateExercise te = new TemplateExercise();
        te.setExercise(squat);
        te.setPosition((short) 1);
        te.setPlannedReps((short) 5);
        te.setPlannedWeightKg(new BigDecimal("100.0"));
        t.addExercise(te);
        templates.saveAndFlush(t);
        em.clear();

        var loaded = templates.findByUserIdOrderByName(owner.getId());
        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getExercises().get(0).getPlannedSets()).isEqualTo((short) 3);
    }

    @Test
    void savesRecordsAndWarnings() {
        Workout w = workoutWithTwoSets();
        WorkoutSet best = w.getExercises().get(0).getSets().get(1);

        PersonalRecord pr = new PersonalRecord();
        pr.setUser(owner);
        pr.setExercise(squat);
        pr.setRecordType(RecordType.MAX_WEIGHT_AT_REPS);
        pr.setReps((short) 5);
        pr.setValue(new BigDecimal("105.00"));
        pr.setWorkoutSet(best);
        pr.setAchievedOn(LocalDate.now());
        records.saveAndFlush(pr);

        OverloadWarning warning = new OverloadWarning();
        warning.setUser(owner);
        warning.setWorkout(w);
        warning.setRuleCode(OverloadRule.WEEKLY_LOAD);
        warning.setMessage("A heti terhelés meghaladta az átlag 130%-át.");
        warnings.saveAndFlush(warning);
        em.clear();

        assertThat(records.findByUserIdAndExerciseIdOrderByAchievedOnDesc(owner.getId(), squat.getId())).hasSize(1);
        assertThat(warnings.findByUserIdAndAcknowledgedFalseOrderByCreatedAtDesc(owner.getId())).hasSize(1);
    }
}

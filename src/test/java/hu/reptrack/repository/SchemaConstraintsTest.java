package hu.reptrack.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.DistanceBand;
import hu.reptrack.dto.EnduranceMetric;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.FitnessLevel;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.OverloadRule;
import hu.reptrack.dto.RecordType;
import hu.reptrack.dto.Role;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * A séma saját védelmei: az enum oszlopok értékei, az egyedi nevek és a zárt edzésre vonatkozó szabály.
 * Minden negatív eset külön teszt, mert a hibás utasítás a PostgreSQL-ben megszakítja a tranzakciót.
 */
@Transactional
class SchemaConstraintsTest extends IntegrationTestBase {

    @Autowired JdbcTemplate jdbc;

    private void insertExercise(String name, String category, String cardioType, Long ownerId) {
        jdbc.update("insert into exercises (name, category, cardio_type, owner_id) values (?, ?, ?, ?)",
                name, category, cardioType, ownerId);
    }

    private Long insertUser(String email) {
        return jdbc.queryForObject("insert into users (email, password_hash, display_name) values (?, 'h', 'T') returning id",
                Long.class, email);
    }

    // ---- az enumok és a CHECK-ek szinkronja ----

    private <E extends Enum<E>> void assertCheckMatches(String table, String column, Class<E> enumClass) {
        List<String> definitions = jdbc.queryForList("""
                select pg_get_constraintdef(c.oid)
                from pg_constraint c
                join pg_attribute a on a.attrelid = c.conrelid and a.attnum = any(c.conkey)
                where c.conrelid = ?::regclass and c.contype = 'c' and a.attname = ?
                  and array_length(c.conkey, 1) = 1 and pg_get_constraintdef(c.oid) like '%ARRAY%'
                """, String.class, table, column);
        assertThat(definitions).as("%s.%s CHECK", table, column).hasSize(1);

        Set<String> allowed = new java.util.HashSet<>();
        Matcher m = Pattern.compile("'([A-Z0-9_]+)'").matcher(definitions.get(0));
        while (m.find()) {
            allowed.add(m.group(1));
        }
        Set<String> expected = Arrays.stream(enumClass.getEnumConstants()).map(Enum::name).collect(Collectors.toSet());
        assertThat(allowed).as("%s.%s engedett értékei egyeznek a(z) %s enummal", table, column, enumClass.getSimpleName())
                .isEqualTo(expected);
    }

    @Test
    void checkConstraintsMatchJavaEnums() {
        assertCheckMatches("users", "role", Role.class);
        assertCheckMatches("users", "fitness_level", FitnessLevel.class);
        assertCheckMatches("exercises", "category", ExerciseCategory.class);
        assertCheckMatches("exercises", "primary_muscle", MuscleGroup.class);
        assertCheckMatches("exercises", "cardio_type", CardioType.class);
        assertCheckMatches("exercise_secondary_muscles", "muscle", MuscleGroup.class);
        assertCheckMatches("goals", "goal_type", GoalType.class);
        assertCheckMatches("goals", "endurance_metric", EnduranceMetric.class);
        assertCheckMatches("personal_records", "record_type", RecordType.class);
        assertCheckMatches("overload_warnings", "rule_code", OverloadRule.class);
        assertCheckMatches("personal_records", "distance_band", DistanceBand.class);
    }

    @Test
    void misspelledCategoryIsRejectedByDatabase() {
        assertThatThrownBy(() -> insertExercise("Teszt guggolás", "WEIGTHS", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void misspelledMuscleIsRejectedByDatabase() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into exercises (name, category, primary_muscle) values ('X', 'WEIGHTS', 'QUADRICEPS')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownSecondaryMuscleIsRejectedByDatabase() {
        insertExercise("Teszt guggolás", "WEIGHTS", null, null);
        Long id = jdbc.queryForObject("select id from exercises where name = 'Teszt guggolás'", Long.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into exercise_secondary_muscles (exercise_id, muscle) values (?, 'LOWER_BACK')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- kardió fajtája ----

    @Test
    void cardioExerciseMustHaveCardioType() {
        assertThatThrownBy(() -> insertExercise("Teszt futás", "CARDIO", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonCardioExerciseMustNotHaveCardioType() {
        assertThatThrownBy(() -> insertExercise("Teszt guggolás", "WEIGHTS", "RUNNING", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cardioWithTypeIsAccepted() {
        insertExercise("Teszt futás", "CARDIO", "RUNNING", null);
        insertExercise("Teszt kerékpár", "CARDIO", "CYCLING", null);
        assertThat(jdbc.queryForObject(
                "select count(*) from exercises where cardio_type is not null and name like 'Teszt %'", Integer.class))
                .isEqualTo(2);
    }

    // ---- egyedi nevek ----

    @Test
    void sharedExerciseNamesAreUniqueCaseInsensitively() {
        insertExercise("Teszt guggolás", "WEIGHTS", null, null);
        assertThatThrownBy(() -> insertExercise("teszt guggolás", "WEIGHTS", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void privateExerciseNamesAreUniquePerUser() {
        Long alice = insertUser("alice@example.com");
        Long bob = insertUser("bob@example.com");
        insertExercise("Saját", "BODYWEIGHT", null, alice);
        insertExercise("Saját", "BODYWEIGHT", null, bob); // másik felhasználónál lehet ugyanaz a név
        assertThatThrownBy(() -> insertExercise("SAJÁT", "BODYWEIGHT", null, alice))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- zárt edzés ----

    @Test
    void closedWorkoutRequiresFatigue() {
        Long user = insertUser("alice@example.com");
        assertThatThrownBy(() -> jdbc.update("""
                insert into workouts (user_id, title, started_at, ended_at)
                values (?, 'E', now() - interval '1 hour', now())""", user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void workoutMustEndAfterItStarted() {
        Long user = insertUser("alice@example.com");
        assertThatThrownBy(() -> jdbc.update("""
                insert into workouts (user_id, title, started_at, ended_at, fatigue)
                values (?, 'E', now(), now() - interval '1 hour', 5)""", user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void openWorkoutNeedsNoFatigue() {
        Long user = insertUser("alice@example.com");
        jdbc.update("insert into workouts (user_id, title, started_at) values (?, 'E', now())", user);
        assertThat(jdbc.queryForObject("select count(*) from workouts", Integer.class)).isEqualTo(1);
    }

    // ---- egyéni csúcsok ----

    private Long prExercise() {
        insertExercise("Teszt PR gyakorlat", "WEIGHTS", null, null);
        return jdbc.queryForObject("select id from exercises where name = 'Teszt PR gyakorlat'", Long.class);
    }

    private void insertRecord(Long user, Long exercise, String type, Integer reps, String band) {
        jdbc.update("insert into personal_records (user_id, exercise_id, record_type, reps, distance_band, value, achieved_on) "
                + "values (?, ?, ?, ?, ?, 100, current_date)", user, exercise, type, reps, band);
    }

    @Test
    void validPersonalRecordShapesAreAccepted() {
        Long user = insertUser("pr@example.com");
        Long exercise = prExercise();
        insertRecord(user, exercise, "MAX_WEIGHT_AT_REPS", 5, null);
        insertRecord(user, exercise, "ESTIMATED_1RM", null, null);
        insertRecord(user, exercise, "SET_VOLUME", null, null);
        insertRecord(user, exercise, "BEST_PACE", null, "FIVE_K");
        assertThat(jdbc.queryForObject("select count(*) from personal_records", Integer.class)).isEqualTo(4);
    }

    @Test
    void weightAtRepsRecordRequiresReps() {
        Long user = insertUser("pr@example.com");
        Long exercise = prExercise();
        assertThatThrownBy(() -> insertRecord(user, exercise, "MAX_WEIGHT_AT_REPS", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneRepMaxRecordMustNotHaveReps() {
        Long user = insertUser("pr@example.com");
        Long exercise = prExercise();
        assertThatThrownBy(() -> insertRecord(user, exercise, "ESTIMATED_1RM", 5, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void paceRecordRequiresDistanceBand() {
        Long user = insertUser("pr@example.com");
        Long exercise = prExercise();
        assertThatThrownBy(() -> insertRecord(user, exercise, "BEST_PACE", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void misspelledDistanceBandIsRejected() {
        Long user = insertUser("pr@example.com");
        Long exercise = prExercise();
        assertThatThrownBy(() -> insertRecord(user, exercise, "BEST_PACE", null, "5K"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- edzéssablonok ----

    private Long insertTemplate(Long user, String name) {
        return jdbc.queryForObject("insert into workout_templates (user_id, name) values (?, ?) returning id",
                Long.class, user, name);
    }

    private void insertTemplateExercise(Long template, Integer sets, Integer reps, String weight) {
        insertExercise("Teszt sablon gyakorlat", "WEIGHTS", null, null);
        Long exercise = jdbc.queryForObject("select id from exercises where name = 'Teszt sablon gyakorlat'", Long.class);
        jdbc.update("insert into template_exercises (template_id, exercise_id, position, planned_sets, planned_reps, "
                + "planned_weight_kg) values (?, ?, 1, ?, ?, ?::numeric)", template, exercise, sets, reps, weight);
    }

    @Test
    void plannedValuesWithinRangeAreAccepted() {
        Long template = insertTemplate(insertUser("t@example.com"), "Láb A");
        insertTemplateExercise(template, 20, 100, "500");
        assertThat(jdbc.queryForObject("select count(*) from template_exercises", Integer.class)).isEqualTo(1);
    }

    @Test
    void plannedSetsMustBeBetweenOneAndTwenty() {
        Long template = insertTemplate(insertUser("t@example.com"), "Láb A");
        assertThatThrownBy(() -> insertTemplateExercise(template, 21, 5, "100"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void plannedSetsCannotBeZero() {
        Long template = insertTemplate(insertUser("t@example.com"), "Láb A");
        assertThatThrownBy(() -> insertTemplateExercise(template, 0, 5, "100"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void plannedRepsMustBeBetweenOneAndHundred() {
        Long template = insertTemplate(insertUser("t@example.com"), "Láb A");
        assertThatThrownBy(() -> insertTemplateExercise(template, 3, 101, "100"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void plannedWeightMustBeBetweenZeroAndFiveHundred() {
        Long template = insertTemplate(insertUser("t@example.com"), "Láb A");
        assertThatThrownBy(() -> insertTemplateExercise(template, 3, 5, "500.5"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void templateNamesAreUniquePerUserCaseInsensitively() {
        Long alice = insertUser("alice@example.com");
        Long bob = insertUser("bob@example.com");
        insertTemplate(alice, "Láb A");
        insertTemplate(bob, "Láb A"); // másik felhasználónál lehet ugyanaz a név
        assertThatThrownBy(() -> insertTemplate(alice, "LÁB a"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- állóképességi célok ----

    private void insertGoal(Long user, String type, String metric, Integer distanceM) {
        jdbc.update("insert into goals (user_id, goal_type, endurance_metric, target_distance_m, target_value) "
                + "values (?, ?, ?, ?, 10)", user, type, metric, distanceM);
    }

    @Test
    void validGoalShapesAreAccepted() {
        Long user = insertUser("goal@example.com");
        insertGoal(user, "ENDURANCE", "DISTANCE", null);
        insertGoal(user, "ENDURANCE", "DURATION", null);
        insertGoal(user, "ENDURANCE", "TIME_FOR_DISTANCE", 5000);
        insertGoal(user, "STRENGTH", null, null);
        assertThat(jdbc.queryForObject("select count(*) from goals", Integer.class)).isEqualTo(4);
    }

    @Test
    void enduranceGoalNeedsAMetric() {
        Long user = insertUser("goal@example.com");
        assertThatThrownBy(() -> insertGoal(user, "ENDURANCE", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void otherGoalsHaveNoMetric() {
        Long user = insertUser("goal@example.com");
        assertThatThrownBy(() -> insertGoal(user, "STRENGTH", "DISTANCE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void timeForDistanceGoalNeedsItsDistance() {
        Long user = insertUser("goal@example.com");
        assertThatThrownBy(() -> insertGoal(user, "ENDURANCE", "TIME_FOR_DISTANCE", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void onlyTimeForDistanceGoalHasADistance() {
        Long user = insertUser("goal@example.com");
        assertThatThrownBy(() -> insertGoal(user, "ENDURANCE", "DISTANCE", 5000))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- gyakorlatképek ----

    @Test
    void everyBuiltInExerciseHasAnImageKey() {
        assertThat(jdbc.queryForObject("select count(*) from exercises where owner_id is null and image_key is null",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select image_key from exercises where owner_id is null and name = 'Guggolás'",
                String.class)).isEqualTo("guggolas");
        assertThat(jdbc.queryForObject("select image_key from exercises where owner_id is null and name = 'Futás'",
                String.class)).isEqualTo("szabadteri-futas");
    }

    @Test
    void imageKeyCannotContainAPath() {
        assertThatThrownBy(() -> jdbc.update("update exercises set image_key = '../titok' where name = 'Guggolás'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- túlterhelési figyelmeztetések ----

    @Test
    void overloadWarningRuleKeyIsUniquePerUserAndRule() {
        Long user = insertUser("ow@example.com");
        String sql = "insert into overload_warnings (user_id, rule_code, rule_key, event_date, message) "
                + "values (?, 'WEEKLY_LOAD', 'week:2026-11-16', current_date, 'm')";
        jdbc.update(sql, user);
        assertThatThrownBy(() -> jdbc.update(sql, user)).isInstanceOf(DataIntegrityViolationException.class);
    }
}

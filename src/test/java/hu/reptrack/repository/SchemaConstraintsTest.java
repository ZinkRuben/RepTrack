package hu.reptrack.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.dto.CardioType;
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
        assertCheckMatches("personal_records", "record_type", RecordType.class);
        assertCheckMatches("overload_warnings", "rule_code", OverloadRule.class);
    }

    @Test
    void misspelledCategoryIsRejectedByDatabase() {
        assertThatThrownBy(() -> insertExercise("Guggolás", "WEIGTHS", null, null))
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
        insertExercise("Guggolás", "WEIGHTS", null, null);
        Long id = jdbc.queryForObject("select id from exercises where name = 'Guggolás'", Long.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into exercise_secondary_muscles (exercise_id, muscle) values (?, 'LOWER_BACK')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- kardió fajtája ----

    @Test
    void cardioExerciseMustHaveCardioType() {
        assertThatThrownBy(() -> insertExercise("Futás", "CARDIO", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonCardioExerciseMustNotHaveCardioType() {
        assertThatThrownBy(() -> insertExercise("Guggolás", "WEIGHTS", "RUNNING", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cardioWithTypeIsAccepted() {
        insertExercise("Futás", "CARDIO", "RUNNING", null);
        insertExercise("Kerékpár", "CARDIO", "CYCLING", null);
        assertThat(jdbc.queryForObject("select count(*) from exercises where cardio_type is not null", Integer.class))
                .isEqualTo(2);
    }

    // ---- egyedi nevek ----

    @Test
    void sharedExerciseNamesAreUniqueCaseInsensitively() {
        insertExercise("Guggolás", "WEIGHTS", null, null);
        assertThatThrownBy(() -> insertExercise("guggolás", "WEIGHTS", null, null))
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
}

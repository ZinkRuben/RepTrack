package hu.reptrack.repository;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.MuscleGroup;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * A gyakorlatkatalógus épségét ellenőrzi, a seed adatokon is: minden sor betölthető (érvénytelen enumérték esetén a
 * Hibernate hibát dobna, és az egész lista használhatatlan lenne), a kardióknak van fajtája, a nevek egyediek.
 * Ez a teszt a későbbi seed migrációkat is lefedi, nem kell módosítani.
 */
@Transactional
class ExerciseCatalogIntegrityTest extends IntegrationTestBase {

    @Autowired ExerciseRepository exercises;
    @Autowired UserRepository users;

    @Test
    void everyExerciseInTheDatabaseLoadsAndIsConsistent() {
        List<Exercise> all = exercises.findAll();

        for (Exercise e : all) {
            assertThat(e.getName()).as("név").isNotBlank();
            assertThat(e.getCategory()).as("%s kategóriája", e.getName()).isNotNull();
            if (e.getCategory() == ExerciseCategory.CARDIO) {
                assertThat(e.getCardioType()).as("%s kardió fajtája", e.getName()).isNotNull();
            } else {
                assertThat(e.getCardioType()).as("%s nem kardió", e.getName()).isNull();
            }
            assertThat(e.getSecondaryMuscles()).as("%s másodlagos izmai", e.getName()).doesNotContainNull();
            if (e.getPrimaryMuscle() != null) {
                assertThat(e.getSecondaryMuscles()).as("%s elsődleges izma nem lehet másodlagos is", e.getName())
                        .doesNotContain(e.getPrimaryMuscle());
            }
        }

        Set<String> sharedNames = all.stream().filter(Exercise::isShared)
                .map(e -> e.getName().toLowerCase()).collect(Collectors.toSet());
        assertThat(sharedNames).as("közös gyakorlatnevek egyediek").hasSize((int) all.stream().filter(Exercise::isShared).count());
    }

    @Test
    void secondaryMuscleIsMatchedExactlyNotByText() {
        User owner = new User();
        owner.setEmail("a@example.com");
        owner.setPasswordHash("h");
        owner.setDisplayName("A");
        users.save(owner);

        Exercise deadlift = exercise("Teszt felhúzás", MuscleGroup.HAMSTRINGS, MuscleGroup.BACK, MuscleGroup.GLUTES);
        Exercise row = exercise("Teszt evezés", MuscleGroup.BACK, MuscleGroup.BICEPS);
        exercises.flush();

        // a seed gyakorlatai is szerepelnek a találatok között, ezért csak a teszt saját gyakorlatait vizsgáljuk
        assertThat(exercises.findBySecondaryMuscle(MuscleGroup.BACK)).extracting(Exercise::getName)
                .contains("Teszt felhúzás").doesNotContain("Teszt evezés");
        assertThat(exercises.findBySecondaryMuscle(MuscleGroup.BICEPS)).extracting(Exercise::getName)
                .contains("Teszt evezés").doesNotContain("Teszt felhúzás");
        assertThat(exercises.findBySecondaryMuscle(MuscleGroup.CHEST)).extracting(Exercise::getName)
                .doesNotContain("Teszt felhúzás", "Teszt evezés");
        assertThat(deadlift.getSecondaryMuscles()).containsExactlyInAnyOrder(MuscleGroup.BACK, MuscleGroup.GLUTES);
        assertThat(row.getCardioType()).isNull();
    }

    @Test
    void cardioTypeIsPersisted() {
        Exercise run = new Exercise();
        run.setName("Teszt futás");
        run.setCategory(ExerciseCategory.CARDIO);
        run.setCardioType(CardioType.RUNNING);
        exercises.saveAndFlush(run);

        assertThat(exercises.findById(run.getId()).orElseThrow().getCardioType()).isEqualTo(CardioType.RUNNING);
    }

    private Exercise exercise(String name, MuscleGroup primary, MuscleGroup... secondary) {
        Exercise e = new Exercise();
        e.setName(name);
        e.setCategory(ExerciseCategory.WEIGHTS);
        e.setPrimaryMuscle(primary);
        e.setSecondaryMuscles(new LinkedHashSet<>(List.of(secondary)));
        return exercises.save(e);
    }
}

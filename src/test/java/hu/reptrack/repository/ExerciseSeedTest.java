package hu.reptrack.repository;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Exercise;
import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.MuscleGroup;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * A V2 migráció 50 közös gyakorlatát ellenőrzi: darabszám, kategóriánként helyes rögzíthető mezők
 * (a WorkoutService ezek alapján validál), minden izomcsoport lefedése és a másodlagos izmok beírása.
 * Az általános épséget (betölthetőség, kardió fajta, egyedi nevek) az ExerciseCatalogIntegrityTest nézi.
 */
@Transactional
class ExerciseSeedTest extends IntegrationTestBase {

    @Autowired ExerciseRepository exercises;

    Map<String, Exercise> seed;

    @BeforeEach
    void loadSeed() {
        seed = exercises.findAll().stream()
                .filter(Exercise::isShared)
                .collect(Collectors.toMap(Exercise::getName, Function.identity()));
    }

    private List<Exercise> inCategory(ExerciseCategory category) {
        return seed.values().stream().filter(e -> e.getCategory() == category).toList();
    }

    @Test
    void containsFiftyExercisesInEveryCategory() {
        assertThat(seed).hasSize(50);
        assertThat(inCategory(ExerciseCategory.WEIGHTS)).hasSize(25);
        assertThat(inCategory(ExerciseCategory.BODYWEIGHT)).hasSize(15);
        assertThat(inCategory(ExerciseCategory.CARDIO)).hasSize(10);
    }

    @Test
    void containsTheExamplesFromTheSpecification() {
        assertThat(seed).containsKeys("Fekvenyomás", "Guggolás", "Futás", "Kerékpározás", "Úszás",
                "Húzódzkodás", "Fekvőtámasz");
    }

    @Test
    void everyExerciseHasDescriptionAndPrimaryMuscle() {
        for (Exercise e : seed.values()) {
            assertThat(e.getDescription()).as("%s leírása", e.getName()).isNotBlank();
            assertThat(e.getPrimaryMuscle()).as("%s elsődleges izma", e.getName()).isNotNull();
            assertThat(e.isArchived()).as("%s nem archivált", e.getName()).isFalse();
        }
    }

    @Test
    void everyMuscleGroupIsPrimaryForAtLeastOneExercise() {
        assertThat(seed.values()).extracting(Exercise::getPrimaryMuscle).contains(MuscleGroup.values());
    }

    @Test
    void weightExercisesTrackWeightAndRepsOnly() {
        for (Exercise e : inCategory(ExerciseCategory.WEIGHTS)) {
            assertThat(e.isTracksWeight() && e.isTracksReps()).as("%s: súly + ismétlés", e.getName()).isTrue();
            assertThat(e.isTracksDuration() || e.isTracksDistance() || e.isTracksHeartRate())
                    .as("%s: nincs idő, táv, pulzus", e.getName()).isFalse();
        }
    }

    @Test
    void bodyweightExercisesTrackEitherRepsOrDuration() {
        for (Exercise e : inCategory(ExerciseCategory.BODYWEIGHT)) {
            assertThat(e.isTracksReps() != e.isTracksDuration())
                    .as("%s: ismétlés vagy időtartam, pontosan az egyik", e.getName()).isTrue();
            assertThat(e.isTracksDistance() || e.isTracksHeartRate())
                    .as("%s: nincs táv, pulzus", e.getName()).isFalse();
        }
        assertThat(seed.get("Alkartámasz").isTracksDuration()).isTrue();
        assertThat(seed.get("Húzódzkodás").isTracksWeight()).as("külső terhelés megadható").isTrue();
    }

    @Test
    void cardioExercisesTrackDurationAndHeartRate() {
        for (Exercise e : inCategory(ExerciseCategory.CARDIO)) {
            assertThat(e.isTracksDuration() && e.isTracksHeartRate())
                    .as("%s: időtartam + pulzus", e.getName()).isTrue();
            assertThat(e.isTracksWeight() || e.isTracksReps()).as("%s: nincs súly, ismétlés", e.getName()).isFalse();
            if (e.getCardioType() == CardioType.RUNNING || e.getCardioType() == CardioType.CYCLING) {
                assertThat(e.isTracksDistance()).as("%s: tempóhoz/sebességhez táv kell", e.getName()).isTrue();
            }
        }
        assertThat(seed.get("Futás").getCardioType()).isEqualTo(CardioType.RUNNING);
        assertThat(seed.get("Kerékpározás").getCardioType()).isEqualTo(CardioType.CYCLING);
        assertThat(seed.get("Úszás").getCardioType()).isEqualTo(CardioType.OTHER);
    }

    @Test
    void secondaryMusclesAreInsertedFromTheSeedArrays() {
        assertThat(seed.get("Fekvenyomás").getSecondaryMuscles())
                .containsExactlyInAnyOrder(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS);
        assertThat(seed.get("Felhúzás").getSecondaryMuscles())
                .containsExactlyInAnyOrder(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.FOREARMS);
        assertThat(seed.get("Futás").getSecondaryMuscles())
                .containsExactlyInAnyOrder(MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES, MuscleGroup.GLUTES);
        assertThat(seed.get("Lábnyújtás gépen").getSecondaryMuscles()).isEmpty();
        assertThat(exercises.findBySecondaryMuscle(MuscleGroup.TRICEPS)).extracting(Exercise::getName)
                .contains("Fekvenyomás", "Fekvőtámasz", "Vállból nyomás rúddal");
    }
}

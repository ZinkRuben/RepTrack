package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseFilter;
import hu.reptrack.dto.ExerciseView;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Gyakorlatok böngészése (UC4) a V2 seed adatain: keresés névre, szűrés kategóriára és izomcsoportra. */
@Transactional
class ExerciseServiceTest extends IntegrationTestBase {

    @Autowired ExerciseService service;
    @Autowired ExerciseRepository exercises;
    @Autowired UserRepository users;

    private List<String> names(ExerciseFilter filter) {
        return service.search(filter).stream().map(ExerciseView::name).toList();
    }

    @Test
    void withoutFilterListsTheWholeSharedCatalog() {
        assertThat(service.search(ExerciseFilter.none())).hasSize(50);
    }

    @Test
    void blankNameDoesNotFilter() {
        assertThat(service.search(new ExerciseFilter("   ", null, null))).hasSize(50);
    }

    @Test
    void nameSearchIsCaseInsensitiveSubstring() {
        assertThat(names(new ExerciseFilter("GUGGOL", null, null))).containsExactlyInAnyOrder(
                "Guggolás", "Bolgár guggolás", "Pisztolyguggolás", "Saját testsúlyos guggolás");
    }

    @Test
    void likeWildcardsAreMatchedLiterally() {
        assertThat(service.search(new ExerciseFilter("%", null, null))).isEmpty();
        assertThat(service.search(new ExerciseFilter("_", null, null))).isEmpty();
    }

    @Test
    void filtersByCategory() {
        List<ExerciseView> cardio = service.search(new ExerciseFilter(null, ExerciseCategory.CARDIO, null));
        assertThat(cardio).hasSize(10).allMatch(e -> e.category() == ExerciseCategory.CARDIO);
    }

    @Test
    void muscleFilterMatchesPrimaryAndSecondaryMuscles() {
        assertThat(names(new ExerciseFilter(null, null, MuscleGroup.TRICEPS)))
                .contains("Francia nyomás", "Tolódzkodás")   // elsődleges
                .contains("Fekvenyomás", "Fekvőtámasz")       // másodlagos
                .doesNotContain("Guggolás");
    }

    @Test
    void filtersCombine() {
        assertThat(names(new ExerciseFilter("támasz", ExerciseCategory.BODYWEIGHT, null)))
                .containsExactlyInAnyOrder("Fekvőtámasz", "Alkartámasz", "Négyütemű fekvőtámasz felugrással");
        assertThat(names(new ExerciseFilter("támasz", ExerciseCategory.BODYWEIGHT, MuscleGroup.CHEST)))
                .containsExactlyInAnyOrder("Fekvőtámasz", "Négyütemű fekvőtámasz felugrással");
    }

    @Test
    void viewCarriesSecondaryMusclesInEnumOrder() {
        ExerciseView bench = service.search(new ExerciseFilter("Fekvenyomás", null, null)).stream()
                .filter(e -> e.name().equals("Fekvenyomás")).findFirst().orElseThrow();
        assertThat(bench.primaryMuscle()).isEqualTo(MuscleGroup.CHEST);
        assertThat(bench.secondaryMuscles()).containsExactly(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS);
        assertThat(bench.tracksWeight()).isTrue();
        assertThat(bench.description()).isNotBlank();
    }

    @Test
    void privateAndArchivedExercisesAreNotListed() {
        User owner = new User();
        owner.setEmail("owner@example.com");
        owner.setPasswordHash("hash");
        owner.setDisplayName("Teszt");
        users.save(owner);

        Exercise mine = new Exercise();
        mine.setName("Saját különleges guggolás");
        mine.setCategory(ExerciseCategory.WEIGHTS);
        mine.setOwner(owner);
        exercises.save(mine);

        Exercise walking = exercises.findAll().stream()
                .filter(e -> e.getName().equals("Gyaloglás")).findFirst().orElseThrow();
        walking.setArchived(true);
        exercises.flush();

        List<String> listed = names(ExerciseFilter.none());
        assertThat(listed).hasSize(49).doesNotContain("Saját különleges guggolás", "Gyaloglás");
    }
}

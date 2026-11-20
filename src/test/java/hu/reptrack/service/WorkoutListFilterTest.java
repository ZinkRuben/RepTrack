package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutFilter;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.dto.WorkoutSummary;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class WorkoutListFilterTest extends IntegrationTestBase {

    @Autowired WorkoutService service;
    @Autowired ExerciseRepository exercises;
    @Autowired UserRepository users;
    @Autowired EntityManager em;

    User alice;
    User bob;
    Exercise squat;
    Exercise run;
    Exercise bike;
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach
    void setUp() {
        alice = users.save(user("alice@example.com"));
        bob = users.save(user("bob@example.com"));
        squat = exercise("Teszt guggolás", ExerciseCategory.WEIGHTS, true, true, false);
        run = exercise("Teszt futás", ExerciseCategory.CARDIO, false, false, true);
        bike = exercise("Teszt kerékpár", ExerciseCategory.CARDIO, false, false, true);

        // alice: W-old (guggolás, 10 napja), W-run (futás, 5 napja), W-mix (guggolás + futás + kerékpár, 1 napja)
        workout(alice, "W-old", 10, squat);
        workout(alice, "W-run", 5, run);
        workout(alice, "W-mix", 1, squat, run, bike);
        workout(bob, "bob-run", 2, run);
        em.flush();
        em.clear();
    }

    private User user(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setDisplayName("Teszt");
        return u;
    }

    private Exercise exercise(String name, ExerciseCategory category, boolean weight, boolean reps, boolean duration) {
        Exercise e = new Exercise();
        e.setName(name);
        e.setCategory(category);
        if (category == ExerciseCategory.CARDIO) {
            e.setCardioType(name.equals("Teszt kerékpár") ? CardioType.CYCLING : CardioType.RUNNING);
        }
        e.setTracksWeight(weight);
        e.setTracksReps(reps);
        e.setTracksDuration(duration);
        return exercises.save(e);
    }

    private void workout(User user, String title, int daysAgo, Exercise... used) {
        Instant start = now.minus(daysAgo, ChronoUnit.DAYS);
        List<WorkoutExerciseRequest> blocks = new java.util.ArrayList<>();
        for (Exercise e : used) {
            WorkoutSetRequest set = e.getCategory() == ExerciseCategory.WEIGHTS
                    ? new WorkoutSetRequest(new BigDecimal("100"), (short) 5, null, null, null, null)
                    : new WorkoutSetRequest(null, null, 1800, null, null, null);
            blocks.add(new WorkoutExerciseRequest(e.getId(), null, List.of(set)));
        }
        service.create(user.getId(), new WorkoutRequest(title, start, null, null, null, null, blocks));
    }

    private List<String> titles(WorkoutFilter filter) {
        return service.list(alice.getId(), filter, PageRequest.of(0, 20)).getContent().stream()
                .map(WorkoutSummary::title).toList();
    }

    @Test
    void withoutFilterListsOwnWorkoutsNewestFirst() {
        assertThat(titles(WorkoutFilter.none())).containsExactly("W-mix", "W-run", "W-old");
    }

    @Test
    void filtersByExercise() {
        assertThat(titles(new WorkoutFilter(null, null, squat.getId(), null))).containsExactly("W-mix", "W-old");
        assertThat(titles(new WorkoutFilter(null, null, bike.getId(), null))).containsExactly("W-mix");
    }

    @Test
    void filtersByCategoryWithoutDuplicates() {
        // a W-mix két kardió gyakorlatot is tartalmaz, mégis egyszer szerepel
        Page<WorkoutSummary> page = service.list(alice.getId(),
                new WorkoutFilter(null, null, null, ExerciseCategory.CARDIO), PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting(WorkoutSummary::title).containsExactly("W-mix", "W-run");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void filtersByTimeRange() {
        // az elmúlt 7 nap: W-run (5 napja) és W-mix (1 napja)
        assertThat(titles(new WorkoutFilter(now.minus(7, ChronoUnit.DAYS), null, null, null)))
                .containsExactly("W-mix", "W-run");
        // a határ: a kezdet benne van, a vég nincs benne
        assertThat(titles(new WorkoutFilter(now.minus(5, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), null, null)))
                .containsExactly("W-run");
    }

    @Test
    void combinesFilters() {
        var filter = new WorkoutFilter(now.minus(7, ChronoUnit.DAYS), null, null, ExerciseCategory.WEIGHTS);
        assertThat(titles(filter)).containsExactly("W-mix");
        assertThat(titles(new WorkoutFilter(now.minus(7, ChronoUnit.DAYS), null, squat.getId(), ExerciseCategory.CARDIO)))
                .isEmpty(); // a guggolás nem kardió
    }

    @Test
    void doesNotLeakOtherUsersWorkouts() {
        assertThat(service.list(bob.getId(), new WorkoutFilter(null, null, null, ExerciseCategory.CARDIO),
                PageRequest.of(0, 20)).getContent()).extracting(WorkoutSummary::title).containsExactly("bob-run");
    }

    @Test
    void pagesAndCountsWithFilter() {
        var filter = new WorkoutFilter(null, null, null, ExerciseCategory.CARDIO);
        Page<WorkoutSummary> first = service.list(alice.getId(), filter, PageRequest.of(0, 1));
        Page<WorkoutSummary> second = service.list(alice.getId(), filter, PageRequest.of(1, 1));

        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).extracting(WorkoutSummary::title).containsExactly("W-mix");
        assertThat(second.getContent()).extracting(WorkoutSummary::title).containsExactly("W-run");
    }

    @Test
    void sortsByAllowedPropertiesOnly() {
        Page<WorkoutSummary> byTitle = service.list(alice.getId(), WorkoutFilter.none(),
                PageRequest.of(0, 20, Sort.by("title")));
        assertThat(byTitle.getContent()).extracting(WorkoutSummary::title).containsExactly("W-mix", "W-old", "W-run");

        assertThatThrownBy(() -> service.list(alice.getId(), WorkoutFilter.none(),
                PageRequest.of(0, 20, Sort.by("user.passwordHash"))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsInvertedTimeRange() {
        assertThatThrownBy(() -> service.list(alice.getId(),
                new WorkoutFilter(now, now.minus(1, ChronoUnit.DAYS), null, null), PageRequest.of(0, 20)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void summaryContainsExerciseCount() {
        assertThat(service.list(alice.getId(), PageRequest.of(0, 20)).getContent().get(0).exerciseCount()).isEqualTo(3);
    }
}

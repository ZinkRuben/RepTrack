package hu.reptrack.security;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.TestPrincipals;
import hu.reptrack.domain.User;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.service.WorkoutService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Közös alap a biztonsági tesztekhez (spring-security-test + MockMvc, valódi PostgreSQL-lel).
 * Három felhasználó: Anna és Béla (USER), valamint egy adminisztrátor. A kéréseket a
 * {@link #as(User)} jelentkezteti be a saját principal típusunkkal (a controllerek az azonosítóját használják).
 */
abstract class SecurityTestSupport extends IntegrationTestBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @Autowired protected ExerciseRepository exercises;
    @Autowired protected WorkoutService workoutService;

    protected User anna;
    protected User bela;
    protected User admin;

    @BeforeEach
    void createUsers() {
        anna = user("anna@example.com", "Anna", Role.USER);
        bela = user("bela@example.com", "Béla", Role.USER);
        admin = user("admin@example.com", "Admin", Role.ADMIN);
    }

    protected User user(String email, String name, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("nem-használt-hash");
        u.setDisplayName(name);
        u.setRole(role);
        return users.save(u);
    }

    /** A kérés a megadott felhasználó nevében (a szerepkörével együtt). */
    protected static RequestPostProcessor as(User user) {
        return TestPrincipals.as(user.getId(), user.getRole());
    }

    protected Long sharedExerciseId(String name) {
        return exercises.findAll().stream().filter(e -> e.isShared() && e.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }

    /** Lezárt edzés egyetlen szettel; a mentés a csúcsokat és a túlterhelési szabályokat is kiértékeli. */
    protected Long workout(User owner, String title, Instant start, String exerciseName, String kg, int reps) {
        WorkoutSetRequest set = new WorkoutSetRequest(new BigDecimal(kg), (short) reps, null, null, null, null);
        WorkoutExerciseRequest exercise = new WorkoutExerciseRequest(sharedExerciseId(exerciseName), null, List.of(set));
        WorkoutRequest request = new WorkoutRequest(title, start, start.plus(1, ChronoUnit.HOURS), (short) 6, null,
                null, List.of(exercise));
        return workoutService.create(owner.getId(), request).id();
    }
}

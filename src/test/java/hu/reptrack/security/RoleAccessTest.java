package hu.reptrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.reptrack.domain.User;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseRequest;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.TemplateExerciseRequest;
import hu.reptrack.dto.TemplateRequest;
import hu.reptrack.service.ExerciseService;
import hu.reptrack.service.TemplateService;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Szerepkörök: a felhasználó egyetlen adminisztrátori végpontot sem ér el, az adminisztrátor mindet, és a
 * szerepkör-hierarchia (ADMIN &gt; USER) révén a felhasználói oldalakat is. A szerepkör űrlapon át nem emelhető.
 */
@AutoConfigureMockMvc
@Transactional
class RoleAccessTest extends SecurityTestSupport {

    @Autowired ExerciseService exerciseService;
    @Autowired TemplateService templateService;

    static Stream<String> adminRoutes() {
        return ProtectedPagesTest.ADMIN.stream();
    }

    static Stream<String> userPages() {
        return ProtectedPagesTest.USER.stream().filter(r -> r.startsWith("GET "));
    }

    private static MockHttpServletRequestBuilder request(String route, Long id) {
        String path = route.substring(route.indexOf(' ') + 1).replace("{id}", String.valueOf(id));
        return route.startsWith("GET ") ? get(path) : post(path).with(csrf());
    }

    private Long ownExercise(User owner, String name) {
        ExerciseRequest r = new ExerciseRequest();
        r.setName(name);
        r.setCategory(ExerciseCategory.WEIGHTS);
        r.setPrimaryMuscle(MuscleGroup.CHEST);
        r.setTracksWeight(true);
        r.setTracksReps(true);
        return exerciseService.createOwn(owner.getId(), r);
    }

    // ---- adminisztrátori végpontok ----

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void userIsForbiddenOnEveryAdminEndpoint(String route) throws Exception {
        Long squat = sharedExerciseId("Guggolás");
        mvc.perform(request(route, squat).with(as(anna))).andExpect(status().isForbidden());
        assertThat(exercises.findById(squat).orElseThrow().isArchived()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("adminRoutes")
    void userCannotManageAnotherAccountThroughTheAdminEndpoints(String route) throws Exception {
        // a fiókkezelő végpontok egy másik felhasználó azonosítójával: Anna nem tilthatja le, törölheti stb. Bélát
        mvc.perform(request(route, bela.getId()).with(as(anna)).param("version", String.valueOf(bela.getVersion()))
                        .param("confirm", "true"))
                .andExpect(status().isForbidden());
        User reloaded = users.findById(bela.getId()).orElseThrow();
        assertThat(reloaded.isEnabled()).isTrue();
        assertThat(reloaded.getRole()).isEqualTo(Role.USER);
        assertThat(reloaded.isPasswordChangeRequired()).isFalse();
    }

    @Test
    void adminReachesTheAdminEndpoints() throws Exception {
        Long squat = sharedExerciseId("Guggolás");
        mvc.perform(get("/admin/exercises/new").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/exercises/" + squat + "/edit").with(as(admin))).andExpect(status().isOk());

        mvc.perform(post("/admin/exercises/" + squat + "/archive").with(csrf()).with(as(admin)))
                .andExpect(redirectedUrl("/exercises?source=SHARED"));
        assertThat(exercises.findById(squat).orElseThrow().isArchived()).isTrue();

        mvc.perform(get("/admin/users").with(as(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/users/" + anna.getId() + "/delete").with(as(admin))).andExpect(status().isOk());
        mvc.perform(post("/admin/users/" + anna.getId() + "/disable").with(csrf()).with(as(admin))
                        .param("version", String.valueOf(anna.getVersion())))
                .andExpect(redirectedUrl("/admin/users"));
        users.flush();
        assertThat(users.findById(anna.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void adminDeletePageOfAMissingAccountIsNotFound() throws Exception {
        mvc.perform(get("/admin/users/999999/delete").with(as(admin))).andExpect(status().isNotFound());
    }

    // ---- felhasználói oldalak ----

    @ParameterizedTest
    @MethodSource("userPages")
    void everyUserPageOpensForUsersAndForAdminsThroughTheHierarchy(String route) throws Exception {
        for (User who : new User[] {anna, admin}) {
            Long id = null;
            if (route.startsWith("GET /workouts/{id}")) {
                id = workout(who, "Saját edzés", Instant.now().minusSeconds(7200), "Guggolás", "60", 5);
            } else if (route.startsWith("GET /exercises/{id}")) {
                id = ownExercise(who, "Saját gyakorlat " + who.getId());
            } else if (route.startsWith("GET /templates/{id}")) {
                id = templateService.create(who.getId(), new TemplateRequest("Saját sablon", List.of(
                        new TemplateExerciseRequest(sharedExerciseId("Guggolás"), (short) 3, null, null, null, null)))).id();
            }
            mvc.perform(request(route, id).with(as(who))).andExpect(status().isOk());
        }
    }

    // ---- jogosultság-emelés ----

    @Test
    void roleCannotBeRaisedThroughTheProfileForm() throws Exception {
        mvc.perform(post("/profile").with(csrf()).with(as(anna))
                        .param("displayName", "Anna Új Neve").param("fitnessLevel", "INTERMEDIATE")
                        .param("weeklyLoadPercent", "130").param("muscleSetsThreshold", "6")
                        .param("fatigueThreshold", "8").param("version", String.valueOf(anna.getVersion()))
                        // ezeket a mezőket az űrlap nem ismeri, a kötésnek figyelmen kívül kell hagynia őket
                        .param("role", "ADMIN").param("enabled", "false").param("email", "tamado@example.com"))
                .andExpect(redirectedUrl("/profile"));

        users.flush();
        User reloaded = users.findById(anna.getId()).orElseThrow();
        assertThat(reloaded.getDisplayName()).as("a mentés megtörtént").isEqualTo("Anna Új Neve");
        assertThat(reloaded.getRole()).isEqualTo(Role.USER);
        assertThat(reloaded.isEnabled()).isTrue();
        assertThat(reloaded.getEmail()).isEqualTo("anna@example.com");
    }
}

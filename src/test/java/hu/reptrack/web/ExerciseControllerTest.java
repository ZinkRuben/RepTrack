package hu.reptrack.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.TestPrincipals;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.domain.Workout;
import hu.reptrack.domain.WorkoutExercise;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseRequest;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.Role;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.repository.WorkoutRepository;
import hu.reptrack.service.ExerciseService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** A gyakorlatok oldalai: lista, saját gyakorlat felvétele és kezelése, közös gyakorlatok adminisztrátornak. */
@AutoConfigureMockMvc
@Transactional
class ExerciseControllerTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ExerciseService exerciseService;
    @Autowired ExerciseRepository exercises;
    @Autowired UserRepository users;
    @Autowired WorkoutRepository workouts;

    User anna;
    User admin;
    RequestPostProcessor asAnna;
    RequestPostProcessor asBela;
    RequestPostProcessor asAdmin;

    @BeforeEach
    void setUp() {
        anna = user("anna@example.com", Role.USER);
        User bela = user("bela@example.com", Role.USER);
        admin = user("admin@example.com", Role.ADMIN);
        asAnna = TestPrincipals.as(anna.getId(), Role.USER);
        asBela = TestPrincipals.as(bela.getId(), Role.USER);
        asAdmin = TestPrincipals.as(admin.getId(), Role.ADMIN);
    }

    private User user(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setDisplayName("Teszt");
        u.setRole(role);
        return users.save(u);
    }

    private Long annasExercise(String name) {
        ExerciseRequest r = new ExerciseRequest();
        r.setName(name);
        r.setCategory(ExerciseCategory.WEIGHTS);
        r.setPrimaryMuscle(MuscleGroup.CHEST);
        r.setTracksWeight(true);
        r.setTracksReps(true);
        return exerciseService.createOwn(anna.getId(), r);
    }

    private Long sharedId(String name) {
        return exercises.findAll().stream().filter(e -> e.isShared() && e.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }

    private void useInWorkout(Long exerciseId) {
        Workout w = new Workout();
        w.setUser(anna);
        w.setTitle("Edzés");
        w.setStartedAt(Instant.now());
        WorkoutExercise we = new WorkoutExercise();
        we.setExercise(exercises.findById(exerciseId).orElseThrow());
        we.setPosition((short) 1);
        w.addExercise(we);
        workouts.saveAndFlush(w);
    }

    private static MockHttpServletRequestBuilder exerciseForm(String url, String name) {
        return post(url).with(csrf())
                .param("name", name).param("category", "WEIGHTS").param("primaryMuscle", "CHEST")
                .param("secondaryMuscles", "TRICEPS").param("_secondaryMuscles", "on")
                .param("tracksWeight", "true").param("tracksReps", "true");
    }

    // ---- lista ----

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/exercises")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void listsTheCatalogWithHungarianLabels() throws Exception {
        mvc.perform(get("/exercises").with(asAnna))
                .andExpect(status().isOk())
                .andExpect(view().name("exercises"))
                .andExpect(content().string(allOf(
                        containsString("50 gyakorlat"), containsString("Fekvenyomás"),
                        containsString("Saját testsúlyos"), containsString("Combfeszítő"),
                        containsString("tempó (perc/km)"), not(containsString("??")))));
    }

    @Test
    void appliesFiltersFromQueryParameters() throws Exception {
        mvc.perform(get("/exercises").with(asAnna).param("category", "CARDIO").param("name", "fut"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("2 gyakorlat"), containsString("Futópados futás"),
                        not(containsString("Fekvenyomás")))));
    }

    @Test
    void emptySelectsMeanNoFilter() throws Exception {
        mvc.perform(get("/exercises").with(asAnna)
                        .param("name", "").param("category", "").param("muscle", "").param("source", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("50 gyakorlat")));
    }

    @Test
    void ownExercisesHaveActionsSharedOnesOnlyForAdmins() throws Exception {
        Long own = annasExercise("Anna nyomása");
        Long squat = sharedId("Guggolás");

        mvc.perform(get("/exercises").with(asAnna))
                .andExpect(content().string(allOf(
                        containsString("51 gyakorlat"), containsString("Anna nyomása"), containsString("Saját"),
                        containsString("/exercises/" + own + "/edit"),
                        not(containsString("/admin/exercises")))));

        mvc.perform(get("/exercises").with(asBela))
                .andExpect(content().string(allOf(containsString("50 gyakorlat"), not(containsString("Anna nyomása")))));

        mvc.perform(get("/exercises").with(asAdmin))
                .andExpect(content().string(allOf(
                        containsString("/admin/exercises/new"), containsString("/admin/exercises/" + squat + "/edit"),
                        not(containsString("Anna nyomása")))));
    }

    // ---- saját gyakorlat ----

    @Test
    void createsOwnExerciseThroughTheForm() throws Exception {
        mvc.perform(get("/exercises/new").with(asAnna))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Új saját gyakorlat"), containsString("name=\"primaryMuscle\""),
                        containsString("name=\"_csrf\""))));

        mvc.perform(exerciseForm("/exercises", "Kábeles mellzárás").with(asAnna))
                .andExpect(redirectedUrl("/exercises?source=OWN"))
                .andExpect(flash().attribute(FlashMessages.SUCCESS, "A(z) „Kábeles mellzárás” gyakorlat létrehozva."));

        Exercise saved = exercises.findAll().stream().filter(e -> e.getName().equals("Kábeles mellzárás"))
                .findFirst().orElseThrow();
        assertThat(saved.getOwner().getId()).isEqualTo(anna.getId());
        assertThat(saved.getSecondaryMuscles()).containsExactly(MuscleGroup.TRICEPS);
    }

    @Test
    void invalidFormShowsHungarianErrors() throws Exception {
        mvc.perform(post("/exercises").with(csrf()).with(asAnna)
                        .param("name", "").param("category", "WEIGHTS").param("tracksWeight", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("exercise-form"))
                .andExpect(content().string(allOf(
                        containsString("Add meg a gyakorlat nevét."),
                        containsString("Válassz elsődleges izomcsoportot."),
                        containsString("Súlyzós gyakorlatnál a súlyt és az ismétlésszámot is rögzíteni kell."))));
    }

    @Test
    void duplicateNameIsShownOnTheNameField() throws Exception {
        annasExercise("Anna nyomása");
        mvc.perform(exerciseForm("/exercises", "anna NYOMÁSA").with(asAnna))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ilyen nevű saját gyakorlatod már van.")));
    }

    @Test
    void someoneElsesExerciseIsNotFound() throws Exception {
        Long own = annasExercise("Anna nyomása");
        mvc.perform(get("/exercises/" + own + "/edit").with(asBela)).andExpect(status().isNotFound());
        mvc.perform(post("/exercises/" + own + "/delete").with(csrf()).with(asBela)).andExpect(status().isNotFound());
        assertThat(exercises.findById(own)).isPresent();
    }

    @Test
    void exerciseInUseHasALockedFormAndCannotBeDeleted() throws Exception {
        Long own = annasExercise("Anna nyomása");
        useInWorkout(own);

        mvc.perform(get("/exercises/" + own + "/edit").with(asAnna))
                .andExpect(content().string(allOf(
                        containsString("nem módosíthatók"), containsString("type=\"hidden\" name=\"category\""))));

        mvc.perform(post("/exercises/" + own + "/delete").with(csrf()).with(asAnna))
                .andExpect(redirectedUrl("/exercises?source=OWN"))
                .andExpect(flash().attribute(FlashMessages.ERROR,
                        "A(z) Anna nyomása gyakorlat már szerepel edzésben, sablonban vagy célban, ezért nem törölhető, csak archiválható."));

        mvc.perform(post("/exercises/" + own + "/archive").with(csrf()).with(asAnna))
                .andExpect(redirectedUrl("/exercises?source=OWN"));
        assertThat(exercises.findById(own).orElseThrow().isArchived()).isTrue();

        mvc.perform(get("/exercises").with(asAnna).param("archived", "true"))
                .andExpect(content().string(allOf(containsString("Anna nyomása"), containsString("Visszaállítás"))));
    }

    @Test
    void editSavesChangesAndStaleVersionIsRejected() throws Exception {
        Long own = annasExercise("Anna nyomása");
        Long version = exercises.findById(own).orElseThrow().getVersion();

        mvc.perform(exerciseForm("/exercises/" + own, "Anna ferde nyomása").param("version", String.valueOf(version)).with(asAnna))
                .andExpect(redirectedUrl("/exercises?source=OWN"));
        exercises.flush();
        assertThat(exercises.findById(own).orElseThrow().getName()).isEqualTo("Anna ferde nyomása");

        mvc.perform(exerciseForm("/exercises/" + own, "Elavult").param("version", String.valueOf(version)).with(asAnna))
                .andExpect(redirectedUrl("/exercises/" + own + "/edit"))
                .andExpect(flash().attributeExists(FlashMessages.ERROR));
    }

    // ---- közös gyakorlatok ----

    @Test
    void usersCannotReachTheAdminPages() throws Exception {
        mvc.perform(get("/admin/exercises/new").with(asAnna)).andExpect(status().isForbidden());
        mvc.perform(exerciseForm("/admin/exercises", "Közös próbálkozás").with(asAnna)).andExpect(status().isForbidden());
        assertThat(exercises.findAll()).noneMatch(e -> e.getName().equals("Közös próbálkozás"));
    }

    @Test
    void adminCreatesAndEditsSharedExercises() throws Exception {
        mvc.perform(get("/admin/exercises/new").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Új közös gyakorlat")));

        mvc.perform(exerciseForm("/admin/exercises", "Kábeles mellzárás").with(asAdmin))
                .andExpect(redirectedUrl("/exercises?source=SHARED"));
        Exercise saved = exercises.findAll().stream().filter(e -> e.getName().equals("Kábeles mellzárás"))
                .findFirst().orElseThrow();
        assertThat(saved.isShared()).isTrue();

        mvc.perform(get("/admin/exercises/" + saved.getId() + "/edit").with(asAdmin))
                .andExpect(content().string(containsString("Közös gyakorlat szerkesztése")));

        mvc.perform(exerciseForm("/admin/exercises", "Guggolás").with(asAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ilyen nevű közös gyakorlat már van.")));

        Long own = annasExercise("Anna nyomása");
        mvc.perform(get("/admin/exercises/" + own + "/edit").with(asAdmin))
                .andExpect(status().isNotFound());
    }
}

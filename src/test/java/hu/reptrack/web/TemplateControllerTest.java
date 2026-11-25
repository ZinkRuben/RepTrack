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
import hu.reptrack.domain.User;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.TemplateExerciseRequest;
import hu.reptrack.dto.TemplateRequest;
import hu.reptrack.dto.TemplateView;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.repository.WorkoutTemplateRepository;
import hu.reptrack.service.TemplateService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Az edzéssablonok oldalai (UC15): lista, új sablon, szerkesztés, törlés, és a hibás űrlap újrarajzolása. */
@AutoConfigureMockMvc
@Transactional
class TemplateControllerTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired TemplateService templateService;
    @Autowired WorkoutTemplateRepository templates;
    @Autowired ExerciseRepository exercises;
    @Autowired UserRepository users;

    User anna;
    RequestPostProcessor asAnna;
    Long squat;
    Long run;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("anna@example.com");
        u.setPasswordHash("hash");
        u.setDisplayName("Anna");
        anna = users.save(u);
        asAnna = TestPrincipals.as(anna.getId(), Role.USER);
        squat = shared("Guggolás");
        run = shared("Futás");
    }

    private Long shared(String name) {
        return exercises.findAll().stream().filter(e -> e.isShared() && e.getName().equals(name))
                .findFirst().orElseThrow().getId();
    }

    private TemplateView legDay() {
        return templateService.create(anna.getId(), new TemplateRequest("Láb A", List.of(
                new TemplateExerciseRequest(squat, (short) 5, (short) 5, new BigDecimal("100.5"), null, null),
                new TemplateExerciseRequest(run, (short) 1, null, null, 1800, 5000))));
    }

    private MockHttpServletRequestBuilder postForm(String url, String name) {
        return post(url).with(csrf()).with(asAnna).param("name", name);
    }

    // ---- lista ----

    @Test
    void listShowsPlannedValuesAndAStartButton() throws Exception {
        Long id = legDay().id();
        mvc.perform(get("/templates").with(asAnna))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Láb A"), containsString("2 gyakorlat"),
                        containsString("5 × 5 ismétlés · 100,5 kg"), containsString("1 × 30 perc · 5 km"),
                        containsString("/workouts/new?template=" + id), containsString("Edzés indítása"),
                        containsString("nem kerül az edzésnaplóba"))));
    }

    @Test
    void emptyListExplainsHowToCreateATemplate() throws Exception {
        mvc.perform(get("/templates").with(asAnna))
                .andExpect(content().string(allOf(containsString("Még nincs sablonod."),
                        containsString("Mentés sablonként"))));
    }

    @Test
    void navbarLinksToTemplates() throws Exception {
        mvc.perform(get("/templates").with(asAnna))
                .andExpect(content().string(allOf(containsString(">Sablonok<"), containsString("class=\"nav-link active\""))));
    }

    // ---- új sablon ----

    @Test
    void newFormExplainsWhichFieldsAreRequired() throws Exception {
        mvc.perform(get("/templates/new").with(asAnna))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Legalább egy gyakorlat kell."),
                        containsString("csak a <strong>szettek száma</strong> kötelező"),
                        containsString("name=\"_csrf\""), containsString("window.CATALOG"),
                        not(containsString("is-invalid")))));
    }

    @Test
    void createConvertsMinutesAndKilometersAndRedirectsToTheList() throws Exception {
        mvc.perform(postForm("/templates", "Láb A")
                        .param("exercises[0].exerciseId", String.valueOf(squat))
                        .param("exercises[0].plannedSets", "5")
                        .param("exercises[0].plannedReps", "5")
                        .param("exercises[0].plannedWeightKg", "100")
                        .param("exercises[1].exerciseId", String.valueOf(run))
                        .param("exercises[1].plannedSets", "1")
                        .param("exercises[1].plannedDurationMin", "30")
                        .param("exercises[1].plannedDistanceKm", "5.5"))
                .andExpect(redirectedUrl("/templates"))
                .andExpect(flash().attribute(FlashMessages.SUCCESS, "A(z) „Láb A” sablon mentve."));

        TemplateView saved = templateService.list(anna.getId()).get(0);
        assertThat(saved.exercises().get(1).plannedDurationSec()).isEqualTo(1800);
        assertThat(saved.exercises().get(1).plannedDistanceM()).isEqualTo(5500);
    }

    @Test
    void duplicateNameMarksTheNameFieldAndKeepsTheRows() throws Exception {
        legDay();
        mvc.perform(postForm("/templates", "láb a")
                        .param("exercises[0].exerciseId", String.valueOf(squat))
                        .param("exercises[0].plannedSets", "3"))
                .andExpect(status().isOk())
                .andExpect(view().name("template-form"))
                .andExpect(content().string(allOf(
                        containsString("is-invalid"), containsString("Ilyen nevű sablonod már van."),
                        containsString("\"exerciseId\":\"" + squat + "\""))));
        assertThat(templates.findAll()).hasSize(1);
    }

    @Test
    void templateWithoutExercisesIsRejected() throws Exception {
        mvc.perform(postForm("/templates", "Üres"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A sablonban legalább egy gyakorlat legyen.")));
    }

    @Test
    void outOfRangeValuesShowTheAllowedRanges() throws Exception {
        mvc.perform(postForm("/templates", "Túl sok")
                        .param("exercises[0].exerciseId", String.valueOf(squat))
                        .param("exercises[0].plannedSets", "25"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("A megadott adatok érvénytelenek."),
                        containsString("a szettek számát (1–20)"))));
        assertThat(templates.findAll()).isEmpty();
    }

    // ---- szerkesztés, törlés ----

    @Test
    void editFormIsPrefilledInMinutesAndKilometers() throws Exception {
        Long id = legDay().id();
        mvc.perform(get("/templates/" + id + "/edit").with(asAnna))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Sablon szerkesztése"), containsString("value=\"Láb A\""),
                        containsString("\"plannedWeightKg\":\"100.5\""),
                        containsString("\"plannedDurationMin\":\"30\""), containsString("\"plannedDistanceKm\":\"5\""))));
    }

    @Test
    void updateAndDelete() throws Exception {
        Long id = legDay().id();
        mvc.perform(postForm("/templates/" + id, "Láb B")
                        .param("exercises[0].exerciseId", String.valueOf(squat))
                        .param("exercises[0].plannedSets", "4"))
                .andExpect(redirectedUrl("/templates"))
                .andExpect(flash().attribute(FlashMessages.SUCCESS, "A(z) „Láb B” sablon mentve."));
        assertThat(templateService.get(anna.getId(), id).exercises()).hasSize(1);

        mvc.perform(post("/templates/" + id + "/delete").with(csrf()).with(asAnna))
                .andExpect(redirectedUrl("/templates"));
        assertThat(templates.findById(id)).isEmpty();
    }
}

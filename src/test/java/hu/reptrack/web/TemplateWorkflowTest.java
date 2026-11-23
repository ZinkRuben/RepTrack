package hu.reptrack.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.TestPrincipals;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.User;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.TemplateExerciseRequest;
import hu.reptrack.dto.TemplateRequest;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.PersonalRecordRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.repository.WorkoutRepository;
import hu.reptrack.service.TemplateService;
import hu.reptrack.service.WorkoutService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * A sablon és az edzés kapcsolata (UC15): edzés indítása sablonból (az új edzés űrlapja kitöltve, de semmi nem
 * mentődik), és az így kitöltött űrlap mentése szokásos edzésként.
 */
@AutoConfigureMockMvc
@Transactional
class TemplateWorkflowTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired TemplateService templateService;
    @Autowired WorkoutService workoutService;
    @Autowired WorkoutRepository workouts;
    @Autowired PersonalRecordRepository records;
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

    private Long template(TemplateExerciseRequest... rows) {
        return templateService.create(anna.getId(), new TemplateRequest("Láb A", List.of(rows))).id();
    }

    // ---- edzés indítása sablonból ----

    @Test
    void startingFromATemplatePrefillsTheFormButSavesNothing() throws Exception {
        Long id = template(
                new TemplateExerciseRequest(squat, (short) 3, (short) 5, new BigDecimal("100.5"), null, null),
                new TemplateExerciseRequest(run, (short) 1, null, null, 1800, 5000));

        MvcResult result = mvc.perform(get("/workouts/new").param("template", String.valueOf(id)).with(asAnna))
                .andExpect(status().isOk())
                .andExpect(view().name("workout-form"))
                .andExpect(content().string(allOf(
                        containsString("Edzés a(z) <strong>„Láb A”</strong> sablonból."),
                        containsString("gombra kerül a naplóba"),
                        containsString("value=\"Láb A\""),
                        not(containsString("Kimaradt")))))
                .andReturn();

        WorkoutForm form = (WorkoutForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getTitle()).isEqualTo("Láb A");
        assertThat(form.getStartedAt()).isCloseTo(LocalDateTime.now(ZoneId.of("Europe/Budapest")),
                within(2, ChronoUnit.MINUTES));
        assertThat(form.getEndedAt()).as("az edzés nyitottként indul").isNull();
        assertThat(form.getExercises()).extracting(WorkoutForm.ExerciseBlock::getExerciseId).containsExactly(squat, run);
        assertThat(form.getExercises().get(0).getSets()).hasSize(3)
                .allSatisfy(s -> {
                    assertThat(s.getReps()).isEqualTo((short) 5);
                    assertThat(s.getWeightKg()).isEqualByComparingTo("100.5");
                });
        WorkoutForm.SetRow runSet = form.getExercises().get(1).getSets().get(0);
        assertThat(runSet.getDurationMin()).isEqualByComparingTo("30");
        assertThat(runSet.getDistanceKm()).isEqualByComparingTo("5");

        assertThat(workouts.count()).as("a megnyitás nem hoz létre edzést").isZero();
    }

    @Test
    void valuesMissingFromTheTemplateStayEmpty() throws Exception {
        Long id = template(new TemplateExerciseRequest(squat, (short) 2, null, null, null, null));

        MvcResult result = mvc.perform(get("/workouts/new").param("template", String.valueOf(id)).with(asAnna))
                .andExpect(status().isOk()).andReturn();
        WorkoutForm form = (WorkoutForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getExercises().get(0).getSets()).hasSize(2)
                .allSatisfy(s -> {
                    assertThat(s.getReps()).isNull();
                    assertThat(s.getWeightKg()).isNull();
                });
    }

    @Test
    void archivedExercisesAreLeftOutWithANotice() throws Exception {
        Long id = template(
                new TemplateExerciseRequest(squat, (short) 3, (short) 5, new BigDecimal("100"), null, null),
                new TemplateExerciseRequest(run, (short) 1, null, null, 1800, null));
        Exercise running = exercises.findById(run).orElseThrow();
        running.setArchived(true);
        exercises.flush();

        MvcResult result = mvc.perform(get("/workouts/new").param("template", String.valueOf(id)).with(asAnna))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kimaradt, mert azóta archiválták:")))
                .andReturn();
        WorkoutForm form = (WorkoutForm) result.getModelAndView().getModel().get("form");
        assertThat(form.getExercises()).extracting(WorkoutForm.ExerciseBlock::getExerciseId).containsExactly(squat);
    }

    @Test
    void unknownTemplateGives404() throws Exception {
        mvc.perform(get("/workouts/new").param("template", "999999").with(asAnna)).andExpect(status().isNotFound());
    }

    @Test
    void savingThePrefilledFormCreatesANormalWorkoutWithRecords() throws Exception {
        template(new TemplateExerciseRequest(squat, (short) 2, (short) 5, new BigDecimal("100"), null, null));
        // egy korábbi edzés, amihez képest a mostani csúcs lesz (az első eredmény még nem PR)
        Instant lastWeek = Instant.now().minus(7, ChronoUnit.DAYS);
        workoutService.create(anna.getId(), new WorkoutRequest("Múlt heti", lastWeek, lastWeek.plus(1, ChronoUnit.HOURS),
                (short) 6, null, null, List.of(new WorkoutExerciseRequest(squat, null,
                        List.of(new WorkoutSetRequest(new BigDecimal("90"), (short) 5, null, null, null, null))))));
        LocalDateTime start = LocalDateTime.now(ZoneId.of("Europe/Budapest")).minusHours(1).truncatedTo(ChronoUnit.MINUTES);

        // a böngésző ugyanazt küldi vissza, amivel az űrlap kitöltődött (a felhasználó a második szettet átírta,
        // és lezárta az edzést: csak a lezárt edzés számít bele a csúcsokba)
        mvc.perform(post("/workouts").with(csrf()).with(asAnna)
                        .param("title", "Láb A").param("startedAt", start.toString())
                        .param("endedAt", start.plusMinutes(50).toString()).param("fatigue", "7")
                        .param("exercises[0].exerciseId", String.valueOf(squat))
                        .param("exercises[0].sets[0].weightKg", "100").param("exercises[0].sets[0].reps", "5")
                        .param("exercises[0].sets[1].weightKg", "105").param("exercises[0].sets[1].reps", "5"))
                .andExpect(status().is3xxRedirection());

        assertThat(workouts.count()).isEqualTo(2);
        assertThat(records.count()).as("a szokásos kiértékelés lefutott: 105 kg × 5 csúcs a múlt heti 90 kg után")
                .isPositive();
    }
}

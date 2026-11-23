package hu.reptrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.reptrack.domain.BodyWeightEntry;
import hu.reptrack.domain.Goal;
import hu.reptrack.domain.OverloadWarning;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseRequest;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.OverloadRule;
import hu.reptrack.dto.TemplateExerciseRequest;
import hu.reptrack.dto.TemplateRequest;
import hu.reptrack.repository.BodyWeightEntryRepository;
import hu.reptrack.repository.GoalRepository;
import hu.reptrack.repository.OverloadWarningRepository;
import hu.reptrack.repository.WorkoutRepository;
import hu.reptrack.repository.WorkoutTemplateRepository;
import hu.reptrack.service.ExerciseService;
import hu.reptrack.service.TemplateService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tulajdonos-ellenőrzés: mindenki kizárólag a saját adatait éri el. Anna adatait (edzések, figyelmeztetés, testtömeg,
 * cél, saját gyakorlat, edzéssablon) sem Béla, sem az adminisztrátor nem látja és nem módosíthatja: a testtömeg és a pulzus
 * egészségügyi adat, az adminisztrátor sem férhet hozzá. A válasz ilyenkor 404, hogy az adat létezése se derüljön ki.
 */
@AutoConfigureMockMvc
@Transactional
class OwnershipTest extends SecurityTestSupport {

    private static final String SECRET = "Anna titkos";

    @Autowired WorkoutRepository workouts;
    @Autowired OverloadWarningRepository warnings;
    @Autowired BodyWeightEntryRepository bodyWeights;
    @Autowired GoalRepository goals;
    @Autowired ExerciseService exerciseService;
    @Autowired TemplateService templateService;
    @Autowired WorkoutTemplateRepository templates;

    Long workoutId;
    Long warningId;
    Long bodyWeightId;
    Long goalId;
    Long exerciseId;
    Long templateId;

    @BeforeEach
    void annasData() {
        Instant start = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(10, ChronoUnit.DAYS);
        workout(anna, SECRET + " lábnapja", start, "Guggolás", "100", 5);
        workoutId = workout(anna, SECRET + " második lábnapja", start.plus(3, ChronoUnit.DAYS), "Guggolás", "110", 5);

        OverloadWarning warning = new OverloadWarning();
        warning.setUser(anna);
        warning.setWorkout(workouts.findById(workoutId).orElseThrow());
        warning.setRuleCode(OverloadRule.HIGH_FATIGUE);
        warning.setRuleKey("teszt");
        warning.setEventDate(LocalDate.now());
        warning.setMessage(SECRET + " figyelmeztetése");
        warningId = warnings.save(warning).getId();

        BodyWeightEntry weight = new BodyWeightEntry();
        weight.setUser(anna);
        weight.setMeasuredOn(LocalDate.now());
        weight.setWeightKg(new BigDecimal("61.7"));
        bodyWeightId = bodyWeights.save(weight).getId();

        Goal goal = new Goal();
        goal.setUser(anna);
        goal.setGoalType(GoalType.STRENGTH);
        goal.setExercise(exercises.findById(sharedExerciseId("Guggolás")).orElseThrow());
        goal.setTargetValue(new BigDecimal("140"));
        goalId = goals.save(goal).getId();

        ExerciseRequest own = new ExerciseRequest();
        own.setName(SECRET + " gyakorlata");
        own.setCategory(ExerciseCategory.WEIGHTS);
        own.setPrimaryMuscle(MuscleGroup.CHEST);
        own.setTracksWeight(true);
        own.setTracksReps(true);
        exerciseId = exerciseService.createOwn(anna.getId(), own);

        templateId = templateService.create(anna.getId(), new TemplateRequest(SECRET + " sablonja", List.of(
                new TemplateExerciseRequest(exerciseId, (short) 3, (short) 8, new BigDecimal("40"), null, null)))).id();
        workouts.flush();
    }

    private RequestPostProcessor intruder(String who) {
        return as(who.equals("admin") ? admin : bela);
    }

    // ---- a tulajdonos eléri (különben a többi teszt semmit sem bizonyítana) ----

    @Test
    void ownerSeesAllOfTheirData() throws Exception {
        mvc.perform(get("/workouts/" + workoutId).with(as(anna)))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString(SECRET + " második lábnapja"),
                        containsString(SECRET + " figyelmeztetése"))));
        mvc.perform(get("/records").with(as(anna))).andExpect(content().string(containsString("Guggolás")));
        mvc.perform(get("/profile").with(as(anna))).andExpect(content().string(containsString("61.7")));
        mvc.perform(get("/exercises/" + exerciseId + "/edit").with(as(anna))).andExpect(status().isOk());
        mvc.perform(get("/templates").with(as(anna))).andExpect(content().string(containsString(SECRET + " sablonja")));
        mvc.perform(get("/templates/" + templateId + "/edit").with(as(anna))).andExpect(status().isOk());
    }

    // ---- más felhasználó és az adminisztrátor ----

    @ParameterizedTest
    @ValueSource(strings = {"bela", "admin"})
    void someoneElsesWorkoutCannotBeOpenedChangedCopiedOrDeleted(String who) throws Exception {
        mvc.perform(get("/workouts/" + workoutId).with(intruder(who))).andExpect(status().isNotFound());
        mvc.perform(get("/workouts/" + workoutId + "/edit").with(intruder(who))).andExpect(status().isNotFound());
        mvc.perform(post("/workouts/" + workoutId).with(csrf()).with(intruder(who))
                        .param("title", "Átírva").param("startedAt", "2026-11-20T10:00").param("version", "0"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/workouts/" + workoutId + "/copy").with(csrf()).with(intruder(who)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/workouts/" + workoutId + "/delete").with(csrf()).with(intruder(who)))
                .andExpect(status().isNotFound());

        workouts.flush();
        assertThat(workouts.findById(workoutId).orElseThrow().getTitle()).isEqualTo(SECRET + " második lábnapja");
        assertThat(workouts.findAll()).as("a másolás sem hozott létre edzést").hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bela", "admin"})
    void someoneElsesWarningBodyWeightAndGoalAreOutOfReach(String who) throws Exception {
        mvc.perform(post("/warnings/" + warningId + "/acknowledge").with(csrf()).with(intruder(who)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/profile/body-weight/" + bodyWeightId + "/delete").with(csrf()).with(intruder(who)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/profile/goals/" + goalId + "/delete").with(csrf()).with(intruder(who)))
                .andExpect(status().isNotFound());

        assertThat(warnings.findById(warningId).orElseThrow().isAcknowledged()).isFalse();
        assertThat(bodyWeights.findById(bodyWeightId)).isPresent();
        assertThat(goals.findById(goalId)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bela", "admin"})
    void someoneElsesPrivateExerciseIsOutOfReach(String who) throws Exception {
        mvc.perform(get("/exercises/" + exerciseId + "/edit").with(intruder(who))).andExpect(status().isNotFound());
        mvc.perform(post("/exercises/" + exerciseId + "/archive").with(csrf()).with(intruder(who)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/exercises/" + exerciseId + "/delete").with(csrf()).with(intruder(who)))
                .andExpect(status().isNotFound());
        // az admin a közös gyakorlatok útvonalán sem éri el
        mvc.perform(get("/admin/exercises/" + exerciseId + "/edit").with(as(admin))).andExpect(status().isNotFound());

        assertThat(exercises.findById(exerciseId).orElseThrow().isArchived()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"bela", "admin"})
    void someoneElsesTemplateCannotBeOpenedChangedOrDeleted(String who) throws Exception {
        mvc.perform(get("/templates/" + templateId + "/edit").with(intruder(who))).andExpect(status().isNotFound());
        mvc.perform(post("/templates/" + templateId).with(csrf()).with(intruder(who))
                        .param("name", "Átírva")
                        .param("exercises[0].exerciseId", String.valueOf(sharedExerciseId("Guggolás")))
                        .param("exercises[0].plannedSets", "3"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/templates/" + templateId + "/delete").with(csrf()).with(intruder(who)))
                .andExpect(status().isNotFound());

        templates.flush();
        assertThat(templates.findById(templateId).orElseThrow().getName()).isEqualTo(SECRET + " sablonja");
    }

    @ParameterizedTest
    @ValueSource(strings = {"bela", "admin"})
    void someoneElsesPrivateExerciseCannotBePutIntoATemplate(String who) throws Exception {
        mvc.perform(post("/templates").with(csrf()).with(intruder(who))
                        .param("name", "Lopott")
                        .param("exercises[0].exerciseId", String.valueOf(exerciseId))
                        .param("exercises[0].plannedSets", "3"))
                .andExpect(status().isNotFound());
        assertThat(templates.findAll()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bela", "admin"})
    void noPageLeaksSomeoneElsesData(String who) throws Exception {
        for (String page : new String[] {"/workouts", "/records", "/stats", "/profile", "/exercises", "/templates"}) {
            mvc.perform(get(page).with(intruder(who)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(allOf(not(containsString(SECRET)), not(containsString("61.7")))));
        }
    }

    // ---- nyílt átirányítás ----

    @Test
    void acknowledgingAWarningNeverRedirectsToAForeignSite() throws Exception {
        for (String back : new String[] {"https://tamado.example/", "//tamado.example/workouts", "/workouts/../login"}) {
            mvc.perform(post("/warnings/" + warningId + "/acknowledge").with(csrf()).with(as(anna)).param("back", back))
                    .andExpect(redirectedUrl("/workouts"));
        }
        mvc.perform(post("/warnings/" + warningId + "/acknowledge").with(csrf()).with(as(anna))
                        .param("back", "/workouts/" + workoutId))
                .andExpect(redirectedUrl("/workouts/" + workoutId));
    }
}

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

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.TestPrincipals;
import hu.reptrack.domain.User;
import hu.reptrack.dto.EnduranceMetric;
import hu.reptrack.dto.GoalView;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.service.ProfileService;
import hu.reptrack.service.WorkoutService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Edzéscélok a profil oldalán: az állóképességi célok felvétele (táv, idő, idő adott távon) és megjelenítése. */
@AutoConfigureMockMvc
@Transactional
class GoalPageTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ProfileService profiles;
    @Autowired WorkoutService workouts;
    @Autowired ExerciseRepository exercises;
    @Autowired UserRepository users;

    User anna;
    RequestPostProcessor asAnna;
    Long run;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("anna@example.com");
        u.setPasswordHash("hash");
        u.setDisplayName("Anna");
        anna = users.save(u);
        asAnna = TestPrincipals.as(anna.getId(), Role.USER);
        run = exercises.findAll().stream().filter(e -> e.isShared() && e.getName().equals("Futás"))
                .findFirst().orElseThrow().getId();
    }

    private MockHttpServletRequestBuilder addGoal(String metric) {
        return post("/profile/goals").with(csrf()).with(asAnna)
                .param("type", "ENDURANCE").param("enduranceMetric", metric).param("exerciseId", String.valueOf(run));
    }

    @Test
    void formOffersTheEnduranceMetricsAndTellsWhichExerciseRecordsWhat() throws Exception {
        mvc.perform(get("/profile").with(asAnna))
                .andExpect(content().string(allOf(
                        containsString("name=\"enduranceMetric\""), containsString("Idő adott távon"),
                        containsString("name=\"targetDistanceKm\""), containsString("name=\"targetTime\""),
                        containsString("data-k=\"true\""), containsString("data-d=\"true\""),
                        not(containsString("a statisztikán és itt is")))));
    }

    @Test
    void timeForDistanceGoalIsSavedFromKilometersAndMinutesSeconds() throws Exception {
        mvc.perform(addGoal("TIME_FOR_DISTANCE").param("targetDistanceKm", "5").param("targetTime", "25:30")
                        .param("targetValue", "999"))                     // a rejtett célérték mezőt figyelmen kívül hagyjuk
                .andExpect(redirectedUrl("/profile#celok"))
                .andExpect(flash().attribute(FlashMessages.SUCCESS, "Az edzéscélt felvettem."));

        GoalView goal = profiles.goals(anna.getId()).get(0);
        assertThat(goal.enduranceMetric()).isEqualTo(EnduranceMetric.TIME_FOR_DISTANCE);
        assertThat(goal.targetDistanceM()).isEqualTo(5000);
        assertThat(goal.targetValue()).isEqualByComparingTo("25.5");

        mvc.perform(get("/profile").with(asAnna))
                .andExpect(content().string(allOf(containsString("Futás"),
                        containsString("cél: 5 km 25:30 alatt (5:06/km)"),
                        containsString("Még nincs legalább 5 km hosszú edzésed ezzel a gyakorlattal."))));
    }

    @Test
    void wrongTimeFormatIsExplained() throws Exception {
        mvc.perform(addGoal("TIME_FOR_DISTANCE").param("targetDistanceKm", "5").param("targetTime", "25 perc"))
                .andExpect(redirectedUrl("/profile#celok"))
                .andExpect(flash().attribute(FlashMessages.ERROR,
                        "A célidőt perc:másodperc alakban add meg, például 25:00 "
                                + "(hosszabb távnál óra:perc:másodperc, például 3:30:00)."));
        assertThat(profiles.goals(anna.getId())).isEmpty();
    }

    @Test
    void distanceGoalShowsKilometersAndTheLongestRun() throws Exception {
        mvc.perform(addGoal("DISTANCE").param("targetValue", "10")).andExpect(redirectedUrl("/profile#celok"));
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        workouts.create(anna.getId(), new WorkoutRequest("Futás", start, start.plus(1, ChronoUnit.HOURS), (short) 5,
                null, null, List.of(new WorkoutExerciseRequest(run, null,
                        List.of(new WorkoutSetRequest(null, null, 2400, 7500, null, null))))));

        mvc.perform(get("/profile").with(asAnna))
                .andExpect(content().string(allOf(
                        containsString("cél: 10 km egyben"), containsString("75% · eddigi leghosszabb: 7,5 km"),
                        not(containsString("10 kg")))));
    }

    @Test
    void timesAreReadInSeveralFormats() {
        assertThat(GoalDisplay.parseTime("25:00")).isEqualByComparingTo("25");
        assertThat(GoalDisplay.parseTime("3:30:00")).isEqualByComparingTo("210");
        assertThat(GoalDisplay.parseTime("45")).isEqualByComparingTo("45");
        assertThat(GoalDisplay.parseTime(" ")).isNull();
        assertThat(GoalDisplay.time(new BigDecimal("210"))).isEqualTo("3:30:00");
        assertThat(GoalDisplay.time(new BigDecimal("27.5"))).isEqualTo("27:30");
    }
}

package hu.reptrack.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.TestPrincipals;
import hu.reptrack.domain.BodyWeightEntry;
import hu.reptrack.domain.User;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.repository.BodyWeightEntryRepository;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.service.WorkoutService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Magyar számformátum mindenhol: tizedesvessző a megjelenített számokban, a böngésző nyelvétől függetlenül (az
 * alkalmazás rögzített magyar locale-lal fut). A beviteli mezők értéke tizedespontos marad, mert a számmezők azt várják.
 */
@AutoConfigureMockMvc
@Transactional
class HungarianFormatTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BodyWeightEntryRepository bodyWeights;
    @Autowired ExerciseRepository exercises;
    @Autowired WorkoutService workoutService;

    User anna;
    RequestPostProcessor asAnna;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("anna@example.com");
        u.setPasswordHash("hash");
        u.setDisplayName("Anna");
        anna = users.save(u);
        asAnna = TestPrincipals.as(anna.getId(), Role.USER);
    }

    @Test
    void profileShowsBodyWeightWithADecimalCommaButKeepsThePointInTheInput() throws Exception {
        BodyWeightEntry weight = new BodyWeightEntry();
        weight.setUser(anna);
        weight.setMeasuredOn(LocalDate.now());
        weight.setWeightKg(new BigDecimal("82.5"));
        bodyWeights.save(weight);

        mvc.perform(get("/profile").with(asAnna).header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("82,5 kg"), not(containsString("82.5 kg")), containsString("value=\"82.5\""))));
    }

    @Test
    void statisticsUseHungarianNumbersEvenForAnEnglishBrowser() throws Exception {
        Long run = exercises.findAll().stream().filter(e -> e.isShared() && e.getName().equals("Futás"))
                .findFirst().orElseThrow().getId();
        Instant start = Instant.now().minus(1, ChronoUnit.DAYS);
        workoutService.create(anna.getId(), new WorkoutRequest("Futás", start, start.plus(90, ChronoUnit.MINUTES),
                (short) 5, null, null, List.of(new WorkoutExerciseRequest(run, null,
                        List.of(new WorkoutSetRequest(null, null, 5400, 12500, null, null))))));

        mvc.perform(get("/stats").with(asAnna).header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("1,5 óra"), containsString("12,5 km"),
                        not(containsString("1.5 óra")), not(containsString("12.5 km")))));
    }
}

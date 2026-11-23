package hu.reptrack.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.TestPrincipals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Az edzés űrlapjának segítségei: a „Most” gomb a befejezéshez, és hogy mit jelent a nyitott edzés. */
@AutoConfigureMockMvc
class WorkoutFormPageTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;

    @Test
    void exercisesArePickedFromCardsWithImagesAndMuscleGroups() throws Exception {
        mvc.perform(get("/workouts/new").with(TestPrincipals.anyUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"exercise-picker-open\""), containsString("/js/exercise-picker.js"),
                        // a beágyazott JSON-ban a Thymeleaf a / jelet \/ alakban írja (a </script> elleni védelem)
                        containsString("images\\/exercises\\/guggolas.jpg"), containsString("\"muscle\":\"QUADS\""),
                        containsString("\"muscleLabel\":"), not(containsString("id=\"exercise-picker\"")))));
        mvc.perform(get("/templates/new").with(TestPrincipals.anyUser()))
                .andExpect(content().string(allOf(
                        containsString("id=\"exercise-picker-open\""), containsString("/js/exercise-picker.js"))));
    }

    @Test
    void endTimeHasANowButtonAndExplainsOpenWorkouts() throws Exception {
        mvc.perform(get("/workouts/new").with(TestPrincipals.anyUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"ended-now\""), containsString("aria-describedby=\"endedAt-help\""),
                        containsString("nem számít bele a csúcsokba és a figyelmeztetésekbe"))));
    }
}

package hu.reptrack.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import hu.reptrack.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** A gyakorlatlista oldal: csak bejelentkezve érhető el, a seed gyakorlatait magyar címkékkel jeleníti meg. */
@AutoConfigureMockMvc
class ExerciseControllerTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;

    @Test
    void requiresLogin() throws Exception {
        mvc.perform(get("/exercises"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser
    void listsTheCatalogWithHungarianLabels() throws Exception {
        mvc.perform(get("/exercises"))
                .andExpect(status().isOk())
                .andExpect(view().name("exercises"))
                .andExpect(content().string(containsString("50 gyakorlat")))
                .andExpect(content().string(containsString("Fekvenyomás")))
                .andExpect(content().string(containsString("Saját testsúlyos")))
                .andExpect(content().string(containsString("Combfeszítő")))
                .andExpect(content().string(containsString("tempó (perc/km)")))
                .andExpect(content().string(not(containsString("??"))));   // nincs hiányzó üzenetkulcs
    }

    @Test
    @WithMockUser
    void appliesFiltersFromQueryParameters() throws Exception {
        mvc.perform(get("/exercises").param("category", "CARDIO").param("name", "fut"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 gyakorlat")))
                .andExpect(content().string(containsString("Futópados futás")))
                .andExpect(content().string(not(containsString("Fekvenyomás"))));
    }

    @Test
    @WithMockUser
    void emptySelectsMeanNoFilter() throws Exception {
        mvc.perform(get("/exercises").param("name", "").param("category", "").param("muscle", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("50 gyakorlat")));
    }
}

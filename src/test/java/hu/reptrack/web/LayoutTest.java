package hu.reptrack.web;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.TestPrincipals;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.StaleDataException;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

/** A közös oldalváz: navigációs sáv (vendég/felhasználó/admin), aktív menüpont, üzenetek és hibaoldal. */
@AutoConfigureMockMvc
@Import(LayoutTest.ThrowingController.class)
class LayoutTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;

    /** Csak a teszthez: a szolgáltatásréteg kivételeit dobja, hogy a hibaoldalra képezést lássuk. */
    @Controller
    static class ThrowingController {
        @GetMapping("/test/not-found")
        String notFound() {
            throw new NotFoundException("A gyakorlat nem található.");
        }

        @GetMapping("/test/business-rule")
        String businessRule() {
            throw new BusinessRuleException("Lezárt edzéshez meg kell adni a fáradtságértéket (1–10).");
        }

        @GetMapping("/test/stale")
        String stale() {
            throw new StaleDataException("Az edzést közben máshol módosították.");
        }
    }

    // ---- navigációs sáv ----

    @Test
    void guestSeesLoginAndRegisterButNoMenu() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Bejelentkezés"), containsString("Regisztráció"),
                        not(containsString("Kijelentkezés")), not(containsString(">Gyakorlatok<")))));
    }

    @Test
    @WithMockUser(username = "anna@example.com")
    void loggedInUserSeesMenuNameAndLogout() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("anna@example.com"), containsString("Kijelentkezés"),
                        containsString(">Gyakorlatok<"),
                        not(containsString("Regisztráció")), not(containsString(">Admin<")))));
    }

    @Test
    void pagesLoadTheirLibrariesFromTheAppNotFromACdn() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(allOf(
                        containsString("/webjars/bootstrap/5.3.3/dist/css/bootstrap.min.css"),
                        containsString("/webjars/bootstrap/5.3.3/dist/js/bootstrap.bundle.min.js"),
                        containsString("/webjars/flatpickr/4.6.13/dist/l10n/hu.js"), containsString("/js/datepicker.js"),
                        not(containsString("jsdelivr")), not(containsString("https://cdn")))));
        mvc.perform(get("/stats").with(hu.reptrack.TestPrincipals.anyUser()))
                .andExpect(content().string(allOf(
                        containsString("/webjars/chart.js/4.4.7/dist/chart.umd.js"), not(containsString("jsdelivr")))));
    }

    @Test
    void themeIsSwitchedWithOneButtonWithoutASystemOption() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(allOf(
                        containsString("data-theme-toggle"), containsString("aria-label=\"Váltás sötét témára\""),
                        not(containsString("data-theme-value")), not(containsString("Rendszer szerint")))));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void adminGetsAdminBadge() throws Exception {
        mvc.perform(get("/")).andExpect(content().string(containsString(">Admin<")));
    }

    @Test
    @WithMockUser
    void currentPageIsMarkedActive() throws Exception {
        mvc.perform(get("/exercises").with(TestPrincipals.anyUser())).andExpect(content().string(allOf(
                containsString("class=\"nav-link active\""), containsString("aria-current=\"page\""))));
        mvc.perform(get("/")).andExpect(content().string(not(containsString("nav-link active"))));
    }

    @Test
    void pagesUseTheSharedLayout() throws Exception {
        mvc.perform(get("/")).andExpect(content().string(allOf(
                containsString("bootstrap.min.css"), containsString("bootstrap.bundle.min.js"),
                containsString("navbar"), containsString("<footer"))));
    }

    // ---- üzenetek ----

    @Test
    @WithMockUser
    void successAndErrorMessagesAreShown() throws Exception {
        mvc.perform(get("/exercises").with(TestPrincipals.anyUser())
                        .flashAttr(FlashMessages.SUCCESS, "Az edzés mentve.")
                        .flashAttr(FlashMessages.ERROR, "A sablon nem törölhető."))
                .andExpect(content().string(allOf(
                        containsString("alert-success"), containsString("Az edzés mentve."),
                        containsString("alert-danger"), containsString("A sablon nem törölhető."))));
    }

    @Test
    @WithMockUser
    void noMessageBoxWithoutMessages() throws Exception {
        mvc.perform(get("/exercises").with(TestPrincipals.anyUser())).andExpect(content().string(allOf(
                not(containsString("alert-success")), not(containsString("alert-danger")))));
    }

    // ---- hibaoldal ----

    @Test
    void errorPageRendersInLayoutInHungarian() throws Exception {
        mvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/nincs-ilyen-oldal"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(allOf(
                        containsString("Az oldal nem található"), containsString("Vissza a kezdőlapra"),
                        containsString("navbar-brand"))));
    }

    @Test
    void unexpectedErrorDoesNotLeakInternalMessage() throws Exception {
        mvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("belső részlet"))
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "belső részlet"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(allOf(
                        containsString("Váratlan hiba történt"), not(containsString("belső részlet")))));
    }

    @Test
    @WithMockUser(username = "anna@example.com")
    void notFoundExceptionBecomes404WithItsMessage() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(allOf(
                        containsString("Az oldal nem található"), containsString("A gyakorlat nem található."),
                        containsString("anna@example.com"))));
    }

    @Test
    @WithMockUser
    void businessRuleExceptionBecomes400WithItsMessage() throws Exception {
        mvc.perform(get("/test/business-rule"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("meg kell adni a fáradtságértéket")));
    }

    @Test
    @WithMockUser
    void staleDataExceptionBecomes409WithItsMessage() throws Exception {
        mvc.perform(get("/test/stale"))
                .andExpect(status().isConflict())
                .andExpect(content().string(allOf(
                        containsString("Ütköző módosítás"), containsString("közben máshol módosították"))));
    }
}

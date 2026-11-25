package hu.reptrack.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.User;
import hu.reptrack.dto.Role;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.service.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * A saját fiók oldalai: jelszó módosítása, a kötelező jelszócsere ideiglenes jelszó után (UC13), és a fiók
 * törlése (UC14).
 */
@AutoConfigureMockMvc
@Transactional
class AccountControllerTest extends IntegrationTestBase {

    private static final String PASSWORD = "Regi-jelszo-1";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    private User anna;

    @BeforeEach
    void createAnna() {
        User u = new User();
        u.setEmail("anna@example.com");
        u.setDisplayName("Anna");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        anna = users.saveAndFlush(u);
    }

    private RequestPostProcessor asAnna(boolean passwordChangeRequired) {
        return user(new AuthenticatedUser(anna.getId(), anna.getEmail(), "Anna", null, Role.USER, true,
                passwordChangeRequired));
    }

    // ---- kötelező jelszócsere ----

    @Test
    void temporaryPasswordLetsTheUserReachOnlyThePasswordPage() throws Exception {
        for (String page : new String[] {"/", "/workouts", "/profile", "/account/delete"}) {
            mvc.perform(get(page).with(asAnna(true))).andExpect(redirectedUrl("/account/password"));
        }
        mvc.perform(post("/workouts/1/delete").with(csrf()).with(asAnna(true)))
                .andExpect(redirectedUrl("/account/password"));
        mvc.perform(get("/account/password").with(asAnna(true)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ideiglenes jelszóval léptél be")));
        mvc.perform(get("/css/app.css").with(asAnna(true))).andExpect(status().isOk());
    }

    @Test
    void afterTheRequiredChangeTheUserStaysLoggedInWithoutTheRestriction() throws Exception {
        anna.setPasswordChangeRequired(true);
        users.saveAndFlush(anna);

        MvcResult result = mvc.perform(post("/account/password").with(csrf()).with(asAnna(true))
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "Uj-jelszo-12").param("newPasswordConfirm", "Uj-jelszo-12"))
                .andExpect(redirectedUrl("/"))
                .andReturn();

        // a munkamenetbe a frissített principal kerül (a többi kérés innen tölti be)
        SecurityContext saved = (SecurityContext) result.getRequest().getSession()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        AuthenticatedUser principal = (AuthenticatedUser) saved.getAuthentication().getPrincipal();
        assertThat(principal.getId()).isEqualTo(anna.getId());
        assertThat(principal.isPasswordChangeRequired()).isFalse();

        users.flush();
        User reloaded = users.findById(anna.getId()).orElseThrow();
        assertThat(reloaded.isPasswordChangeRequired()).isFalse();
        assertThat(passwordEncoder.matches("Uj-jelszo-12", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void passwordFormShowsErrors() throws Exception {
        mvc.perform(post("/account/password").with(csrf()).with(asAnna(false))
                        .param("currentPassword", "rossz-jelszo")
                        .param("newPassword", "Uj-jelszo-12").param("newPasswordConfirm", "Uj-jelszo-12"))
                .andExpect(status().isOk()).andExpect(view().name("account-password"))
                .andExpect(content().string(containsString("A jelenlegi jelszó hibás.")));
        mvc.perform(post("/account/password").with(csrf()).with(asAnna(false))
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "Uj-jelszo-12").param("newPasswordConfirm", "masik-jelszo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A két jelszó nem egyezik.")));
        assertThat(passwordEncoder.matches(PASSWORD, users.findById(anna.getId()).orElseThrow().getPasswordHash()))
                .isTrue();
    }

    @Test
    void voluntaryChangeReturnsToTheProfile() throws Exception {
        mvc.perform(post("/account/password").with(csrf()).with(asAnna(false))
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "Uj-jelszo-12").param("newPasswordConfirm", "Uj-jelszo-12"))
                .andExpect(redirectedUrl("/profile"));
    }

    // ---- fiók törlése ----

    @Test
    void deletePageListsWhatWillBeDeleted() throws Exception {
        mvc.perform(get("/account/delete").with(asAnna(false)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ezek törlődnek")))
                .andExpect(content().string(containsString("name=\"password\"")));
    }

    @Test
    void deletionNeedsTheConfirmationAndTheCorrectPassword() throws Exception {
        mvc.perform(post("/account/delete").with(csrf()).with(asAnna(false)).param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("a törlés végleges")));
        mvc.perform(post("/account/delete").with(csrf()).with(asAnna(false))
                        .param("password", "rossz-jelszo").param("confirm", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A jelszó hibás")));
        assertThat(users.findById(anna.getId())).isPresent();
    }

    @Test
    void deletingTheAccountLogsOut() throws Exception {
        mvc.perform(post("/account/delete").with(csrf()).with(asAnna(false))
                        .param("password", PASSWORD).param("confirm", "true"))
                .andExpect(redirectedUrl("/login?deleted"))
                .andExpect(unauthenticated());
        assertThat(users.findById(anna.getId())).isEmpty();

        mvc.perform(get("/login").param("deleted", ""))
                .andExpect(content().string(containsString("A fiókodat és minden adatodat töröltük.")));
    }

    @Test
    void profileLinksToThePasswordAndDeletePages() throws Exception {
        mvc.perform(get("/profile").with(asAnna(false)))
                .andExpect(content().string(containsString("href=\"/account/password\"")))
                .andExpect(content().string(containsString("href=\"/account/delete\"")));
    }
}

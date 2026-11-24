package hu.reptrack.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.TestPrincipals;
import hu.reptrack.domain.User;
import hu.reptrack.dto.BodyWeightRequest;
import hu.reptrack.dto.Role;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.service.ProfileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** Az adminisztrátori fiókkezelés oldalai (UC13). */
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProfileService profileService;

    private User admin;
    private User anna;

    @BeforeEach
    void createUsers() {
        admin = user("admin@example.com", "Admin", Role.ADMIN);
        anna = user("anna@example.com", "Kiss Anna", Role.USER);
    }

    private User user(String email, String name, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setDisplayName(name);
        u.setPasswordHash("nem-használt-hash");
        u.setRole(role);
        return users.saveAndFlush(u);
    }

    private RequestPostProcessor asAdmin() {
        return TestPrincipals.as(admin.getId(), Role.ADMIN);
    }

    private String version(User u) {
        users.flush();
        return String.valueOf(users.findById(u.getId()).orElseThrow().getVersion());
    }

    @Test
    void listShowsAccountsButNoHealthData() throws Exception {
        profileService.saveBodyWeight(anna.getId(), new BodyWeightRequest(LocalDate.now(), new BigDecimal("71.3")));

        mvc.perform(get("/admin/users").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("anna@example.com")))
                .andExpect(content().string(containsString("Kiss Anna")))
                .andExpect(content().string(not(containsString("71.3"))))
                .andExpect(content().string(not(containsString("71,3"))));
    }

    @Test
    void navbarLinksToTheUsersPageOnlyForAdmins() throws Exception {
        mvc.perform(get("/workouts").with(asAdmin())).andExpect(content().string(containsString("href=\"/admin/users\"")));
        mvc.perform(get("/workouts").with(TestPrincipals.as(anna.getId(), Role.USER)))
                .andExpect(content().string(not(containsString("href=\"/admin/users\""))));
    }

    @Test
    void searchKeepsTheQueryAfterAnAction() throws Exception {
        mvc.perform(get("/admin/users").param("q", "kiss").with(asAdmin()))
                .andExpect(content().string(containsString("anna@example.com")))
                .andExpect(content().string(not(containsString("admin@example.com</td>"))));

        mvc.perform(post("/admin/users/" + anna.getId() + "/disable").with(csrf()).with(asAdmin())
                        .param("version", version(anna)).param("q", "kiss"))
                .andExpect(redirectedUrl("/admin/users?q=kiss"))
                .andExpect(flash().attribute(FlashMessages.SUCCESS, containsString("letiltottam")));
        assertThat(users.findById(anna.getId()).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void passwordResetShowsTheTemporaryPasswordOnce() throws Exception {
        mvc.perform(post("/admin/users/" + anna.getId() + "/reset-password").with(csrf()).with(asAdmin())
                        .param("version", version(anna)))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("tempPassword", matchesPattern("[a-zA-Z2-9]{14}")))
                .andExpect(flash().attribute("tempPasswordFor", "anna@example.com"));
        assertThat(users.findById(anna.getId()).orElseThrow().isPasswordChangeRequired()).isTrue();
    }

    @Test
    void promoteAndDemote() throws Exception {
        mvc.perform(post("/admin/users/" + anna.getId() + "/promote").with(csrf()).with(asAdmin())
                        .param("version", version(anna)))
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(users.findById(anna.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);

        mvc.perform(post("/admin/users/" + anna.getId() + "/demote").with(csrf()).with(asAdmin())
                        .param("version", version(anna)))
                .andExpect(redirectedUrl("/admin/users"));
        assertThat(users.findById(anna.getId()).orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void ruleViolationsAndStaleVersionsBecomeErrorMessages() throws Exception {
        mvc.perform(post("/admin/users/" + admin.getId() + "/disable").with(csrf()).with(asAdmin())
                        .param("version", version(admin)))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute(FlashMessages.ERROR, containsString("saját fiókodat")));
        mvc.perform(post("/admin/users/" + anna.getId() + "/disable").with(csrf()).with(asAdmin())
                        .param("version", "999"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute(FlashMessages.ERROR, containsString("közben módosították")));
        assertThat(users.findById(anna.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void deletionHasAConfirmationPage() throws Exception {
        mvc.perform(get("/admin/users/" + anna.getId() + "/delete").with(asAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("anna@example.com")))
                .andExpect(content().string(containsString("nem vonható vissza")));

        mvc.perform(post("/admin/users/" + anna.getId() + "/delete").with(csrf()).with(asAdmin())
                        .param("version", version(anna)))
                .andExpect(redirectedUrl("/admin/users/" + anna.getId() + "/delete"));
        assertThat(users.findById(anna.getId())).isPresent();

        mvc.perform(post("/admin/users/" + anna.getId() + "/delete").with(csrf()).with(asAdmin())
                        .param("version", version(anna)).param("confirm", "true"))
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute(FlashMessages.SUCCESS, containsString("töröltem")));
        assertThat(users.findById(anna.getId())).isEmpty();
    }

    @Test
    void ownDeletePageRedirectsToTheAccountPage() throws Exception {
        mvc.perform(get("/admin/users/" + admin.getId() + "/delete").with(asAdmin()))
                .andExpect(redirectedUrl("/account/delete"));
    }
}

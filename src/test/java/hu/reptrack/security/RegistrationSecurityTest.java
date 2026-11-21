package hu.reptrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.reptrack.domain.User;
import hu.reptrack.dto.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regisztráció és munkamenet, a valódi bejelentkezési folyamaton át: a regisztráció nem ad jogosultságot, a jelszó
 * hash-ként tárolódik, a kijelentkezés pedig érvényteleníti a munkamenetet. A bejelentkezéskori
 * munkamenet-azonosító cserét (session fixation) a SessionFixationTest ellenőrzi valódi szerverrel. (Az űrlap validációját és a hibaüzeneteket az AuthControllerTest is lefedi.)
 */
@AutoConfigureMockMvc
@Transactional
class RegistrationSecurityTest extends SecurityTestSupport {

    private static final String PASSWORD = "Erős-jelszó-2026";

    @Autowired PasswordEncoder passwordEncoder;

    private void register(String email, String... extraParams) throws Exception {
        var request = post("/register").with(csrf())
                .param("displayName", "Új Felhasználó").param("email", email)
                .param("password", PASSWORD).param("passwordConfirm", PASSWORD);
        for (int i = 0; i < extraParams.length; i += 2) {
            request.param(extraParams[i], extraParams[i + 1]);
        }
        mvc.perform(request).andExpect(redirectedUrl("/login"));
    }

    /** Bejelentkezés az űrlapon át a megadott munkamenetben; visszaadja a kérés utáni munkamenetet. */
    private MockHttpSession login(MockHttpSession session, String email) throws Exception {
        MvcResult result = mvc.perform(post("/login").with(csrf()).session(session)
                        .param("email", email).param("password", PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withRoles("USER"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void registrationCannotGrantRolesOrChangeAccountStatus() throws Exception {
        register("uj@example.com",
                "role", "ADMIN", "enabled", "false", "id", String.valueOf(admin.getId()), "passwordHash", "x");

        User created = users.findByEmailIgnoreCase("uj@example.com").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.USER);
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getId()).isNotEqualTo(admin.getId());
        assertThat(users.findById(admin.getId()).orElseThrow().getRole()).as("a meglévő admin érintetlen").isEqualTo(Role.ADMIN);
    }

    @Test
    void passwordIsStoredOnlyAsBcryptHash() throws Exception {
        register("uj@example.com");

        String hash = users.findByEmailIgnoreCase("uj@example.com").orElseThrow().getPasswordHash();
        assertThat(hash).doesNotContain(PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void registeredUserCanLogInButIsNotAnAdmin() throws Exception {
        register("uj@example.com");
        MockHttpSession session = login(new MockHttpSession(), "uj@example.com");

        mvc.perform(get("/workouts").session(session)).andExpect(status().isOk());
        mvc.perform(get("/admin/exercises/new").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        register("uj@example.com");
        MockHttpSession session = login(new MockHttpSession(), "uj@example.com");
        mvc.perform(get("/workouts").session(session)).andExpect(status().isOk());

        mvc.perform(post("/logout").with(csrf()).session(session)).andExpect(redirectedUrl("/login?logout"));

        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/workouts").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void protectedPageAsksForLoginBeforeRegistration() throws Exception {
        mvc.perform(get("/workouts")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/register")).andExpect(status().isOk());
    }
}

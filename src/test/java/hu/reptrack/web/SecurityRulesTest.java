package hu.reptrack.web;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.TestPrincipals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.MockMvc;

/** Szerepkör-hierarchia (ADMIN > USER) URL-, metódus- és sablonszinten, valamint a CSRF-védelem. */
@AutoConfigureMockMvc
@Import(SecurityRulesTest.GuardedService.class)
class SecurityRulesTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired GuardedService guarded;

    /** Csak a teszthez: így védik majd a szolgáltatások az adminisztrátori műveleteket. */
    @Service
    static class GuardedService {
        @PreAuthorize("hasRole('USER')")
        void forUsers() {
        }

        @PreAuthorize("hasRole('ADMIN')")
        void forAdmins() {
        }
    }

    // ---- URL-szabályok ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanOpenUserPagesThroughTheHierarchy() throws Exception {
        mvc.perform(get("/exercises").with(TestPrincipals.anyAdmin())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotOpenAdminPages() throws Exception {
        mvc.perform(get("/admin/users")).andExpect(status().isForbidden());
    }

    @Test
    void adminPassesTheAdminRule() throws Exception {
        mvc.perform(get("/admin/users").with(TestPrincipals.anyAdmin())).andExpect(status().isOk());
    }

    @Test
    void guestIsSentToLoginFromAdminPages() throws Exception {
        mvc.perform(get("/admin/users")).andExpect(redirectedUrl("/login"));
    }

    // ---- metódusszintű jogosultság ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCallUserMethodsThroughTheHierarchy() {
        assertThatCode(guarded::forUsers).doesNotThrowAnyException();
        assertThatCode(guarded::forAdmins).doesNotThrowAnyException();
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCannotCallAdminMethods() {
        assertThatCode(guarded::forUsers).doesNotThrowAnyException();
        assertThatThrownBy(guarded::forAdmins).isInstanceOf(AccessDeniedException.class);
    }

    // ---- sablonok ----

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminSeesTheUserMenuInTheNavbar() throws Exception {
        mvc.perform(get("/")).andExpect(content().string(containsString(">Gyakorlatok<")));
    }

    // ---- CSRF ----

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/register").param("email", "anna@example.com")).andExpect(status().isForbidden());
        mvc.perform(post("/login").param("email", "anna@example.com").param("password", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void logoutNeedsCsrfToken() throws Exception {
        mvc.perform(post("/logout")).andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(csrf())).andExpect(redirectedUrl("/login?logout"));
    }

    // ---- HTTPS nélkül (fejlesztés): nincs átirányítás, a HSTS alapértelmezés szerint működik ----

    @Test
    void withoutSslPlainHttpIsServedAndSecureRequestsGetHsts() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/login").secure(true))
                .andExpect(header().exists("Strict-Transport-Security"));
    }
}

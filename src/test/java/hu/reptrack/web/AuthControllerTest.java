package hu.reptrack.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.User;
import hu.reptrack.dto.RegistrationRequest;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.service.AuthenticatedUser;
import hu.reptrack.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Saját bejelentkezési és regisztrációs oldal, valódi felhasználókkal az adatbázisból. */
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired UserRepository users;

    private void registerAnna() {
        userService.register(new RegistrationRequest("Teszt Anna", "anna@example.com", "titkos123", "titkos123"));
    }

    private static MockHttpServletRequestBuilder registration(String name, String email, String password,
                                                              String confirm) {
        return post("/register").with(csrf())
                .param("displayName", name).param("email", email)
                .param("password", password).param("passwordConfirm", confirm);
    }

    // ---- oldalak ----

    @Test
    void loginAndRegisterPagesArePublicAndUseTheLayout() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("name=\"email\""), containsString("name=\"password\""),
                        containsString("name=\"_csrf\""), containsString("navbar-brand"))));
        mvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("name=\"displayName\""), containsString("name=\"passwordConfirm\""),
                        containsString("name=\"_csrf\""),
                        not(containsString("is-invalid")), not(containsString("invalid-feedback\"")))));
    }

    @Test
    @WithMockUser
    void loggedInUserIsSentHomeFromLoginAndRegister() throws Exception {
        mvc.perform(get("/login")).andExpect(redirectedUrl("/"));
        mvc.perform(get("/register")).andExpect(redirectedUrl("/"));
    }

    // ---- regisztráció ----

    @Test
    void successfulRegistrationRedirectsToLoginWithMessageAndPrefilledEmail() throws Exception {
        MvcResult result = mvc.perform(registration("Teszt Anna", "Anna@Example.com", "titkos123", "titkos123"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute(FlashMessages.SUCCESS, "Sikeres regisztráció! Most már bejelentkezhetsz."))
                .andReturn();
        assertThat(users.findByEmailIgnoreCase("anna@example.com")).isPresent();

        mvc.perform(get("/login").flashAttrs(result.getFlashMap()))
                .andExpect(content().string(allOf(
                        containsString("Sikeres regisztráció!"), containsString("value=\"Anna@Example.com\""))));
    }

    @Test
    void invalidRegistrationShowsHungarianFieldErrorsAndDoesNotEchoPasswords() throws Exception {
        mvc.perform(registration("", "nem-email", "rovid", "masik-jelszo"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(allOf(
                        containsString("Add meg a neved."), containsString("Érvénytelen e-mail-cím."),
                        containsString("A jelszó legalább 8 karakter legyen."),
                        containsString("A két jelszó nem egyezik."), containsString("is-invalid"),
                        containsString("value=\"nem-email\""),
                        not(containsString("rovid")), not(containsString("masik-jelszo")))));
        assertThat(users.findByEmailIgnoreCase("nem-email")).isEmpty();
    }

    @Test
    void duplicateEmailIsShownOnTheEmailField() throws Exception {
        registerAnna();
        mvc.perform(registration("Másik Anna", "ANNA@example.com", "titkos456", "titkos456"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString("Ezzel az e-mail-címmel már regisztráltak.")));
    }

    // ---- bejelentkezés, kijelentkezés ----

    @Test
    void registeredUserCanLogInWithEmailInAnyCase() throws Exception {
        registerAnna();
        Long annaId = users.findByEmailIgnoreCase("anna@example.com").orElseThrow().getId();
        mvc.perform(formLogin("/login").userParameter("email").user("Anna@Example.com").password("titkos123"))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("anna@example.com").withRoles("USER")
                        .withAuthentication(auth -> {
                            // a principal a saját típusunk, benne az azonosítóval és a névvel; a hash nem marad meg
                            assertThat(auth.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
                            AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
                            assertThat(user.getId()).isEqualTo(annaId);
                            assertThat(user.getDisplayName()).isEqualTo("Teszt Anna");
                            assertThat(user.getPassword()).isNull();
                        }));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameHungarianError() throws Exception {
        registerAnna();
        for (String[] attempt : new String[][] {{"anna@example.com", "rossz-jelszo"}, {"senki@example.com", "titkos123"}}) {
            MvcResult result = mvc.perform(formLogin("/login").userParameter("email")
                            .user(attempt[0]).password(attempt[1]))
                    .andExpect(redirectedUrl("/login?error"))
                    .andExpect(unauthenticated())
                    .andReturn();
            mvc.perform(get("/login").param("error", "").session((MockHttpSession) result.getRequest().getSession()))
                    .andExpect(content().string(allOf(
                            containsString("alert-danger"), containsString("Hibás e-mail-cím vagy jelszó."))));
        }
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        registerAnna();
        User anna = users.findByEmailIgnoreCase("anna@example.com").orElseThrow();
        anna.setEnabled(false);
        users.flush();

        MvcResult result = mvc.perform(formLogin("/login").userParameter("email")
                        .user("anna@example.com").password("titkos123"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated())
                .andReturn();
        mvc.perform(get("/login").param("error", "").session((MockHttpSession) result.getRequest().getSession()))
                .andExpect(content().string(containsString("A fiókodat letiltották.")));
    }

    @Test
    void logoutRedirectsToLoginWithMessage() throws Exception {
        mvc.perform(post("/logout").with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
        mvc.perform(get("/login").param("logout", ""))
                .andExpect(content().string(allOf(
                        containsString("alert-success"), containsString("Sikeresen kijelentkeztél."))));
    }

    @Test
    void protectedPageRedirectsGuestToLoginAndBackAfterLogin() throws Exception {
        registerAnna();
        MvcResult denied = mvc.perform(get("/exercises")).andExpect(redirectedUrl("/login")).andReturn();
        MockHttpSession session = (MockHttpSession) denied.getRequest().getSession();

        mvc.perform(post("/login").with(csrf()).session(session)
                        .param("email", "anna@example.com").param("password", "titkos123"))
                .andExpect(redirectedUrl("http://localhost/exercises?continue"));
    }
}

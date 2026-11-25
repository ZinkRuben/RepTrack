package hu.reptrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Védett oldalak: az alkalmazás minden végpontja be van sorolva (nyilvános / felhasználói / adminisztrátori), és
 * ennek megfelelően védett. Ha valaki új végpontot vesz fel, az {@link #everyEndpointIsClassified()} teszt addig bukik,
 * amíg az itt nincs besorolva, így új oldal nem maradhat véletlenül védelem nélkül.
 */
@AutoConfigureMockMvc
@Transactional
class ProtectedPagesTest extends SecurityTestSupport {

    // ---- az alkalmazás összes MVC-végpontja ("METÓDUS útvonal") ----

    static final List<String> PUBLIC = List.of(
            "GET /", "GET /login", "GET /register", "POST /register");

    static final List<String> USER = List.of(
            "GET /workouts", "GET /workouts/new", "GET /workouts/{id}", "GET /workouts/{id}/edit",
            "POST /workouts", "POST /workouts/{id}", "POST /workouts/{id}/delete", "POST /workouts/{id}/copy",
            "GET /templates", "GET /templates/new", "GET /templates/{id}/edit",
            "POST /templates", "POST /templates/{id}", "POST /templates/{id}/delete",
            "POST /warnings/{id}/acknowledge",
            "GET /exercises", "GET /exercises/new", "GET /exercises/{id}/edit",
            "POST /exercises", "POST /exercises/{id}", "POST /exercises/{id}/archive", "POST /exercises/{id}/restore",
            "POST /exercises/{id}/delete",
            "GET /records", "GET /stats",
            "GET /profile", "POST /profile", "POST /profile/body-weight", "POST /profile/body-weight/{id}/delete",
            "POST /profile/goals", "POST /profile/goals/{id}/delete",
            "GET /account/password", "POST /account/password", "GET /account/delete", "POST /account/delete");

    static final List<String> ADMIN = List.of(
            "GET /admin/exercises/new", "GET /admin/exercises/{id}/edit",
            "POST /admin/exercises", "POST /admin/exercises/{id}", "POST /admin/exercises/{id}/archive",
            "POST /admin/exercises/{id}/restore", "POST /admin/exercises/{id}/delete",
            "GET /admin/users", "GET /admin/users/{id}/delete",
            "POST /admin/users/{id}/disable", "POST /admin/users/{id}/enable", "POST /admin/users/{id}/promote",
            "POST /admin/users/{id}/demote", "POST /admin/users/{id}/reset-password", "POST /admin/users/{id}/delete");

    /** A Spring Security saját végpontjai (szűrő kezeli őket, nem controller). */
    static final List<String> SECURITY_FILTER_POSTS = List.of("POST /login", "POST /logout");

    static Stream<String> protectedGets() {
        return Stream.concat(USER.stream(), ADMIN.stream()).filter(r -> r.startsWith("GET "));
    }

    static Stream<String> protectedPosts() {
        return Stream.concat(USER.stream(), ADMIN.stream()).filter(r -> r.startsWith("POST "));
    }

    static Stream<String> allPosts() {
        return Stream.of(PUBLIC, USER, ADMIN, SECURITY_FILTER_POSTS).flatMap(List::stream)
                .filter(r -> r.startsWith("POST "));
    }

    static Stream<String> publicPaths() {
        return Stream.of("/", "/login", "/register", "/css/app.css", "/js/app.js",
                "/webjars/bootstrap/5.3.3/dist/css/bootstrap.min.css",
                "/webjars/bootstrap/5.3.3/dist/js/bootstrap.bundle.min.js",
                "/webjars/bootstrap-icons/1.11.3/font/bootstrap-icons.min.css",
                "/webjars/bootstrap-icons/1.11.3/font/fonts/bootstrap-icons.woff2",
                "/webjars/fontsource-variable__inter/5.0.15/index.css",
                "/webjars/chart.js/4.4.7/dist/chart.umd.js",
                "/webjars/flatpickr/4.6.13/dist/flatpickr.min.js", "/webjars/flatpickr/4.6.13/dist/l10n/hu.js",
                "/js/datepicker.js", "/js/exercise-picker.js", "/images/exercises/guggolas.jpg");
    }

    /** "GET /workouts/{id}" → "/workouts/1" (a jogosultság-ellenőrzés az adat betöltése előtt történik). */
    private static String path(String route) {
        return route.substring(route.indexOf(' ') + 1).replace("{id}", "1");
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    // ---- besorolás ----

    @Test
    void everyEndpointIsClassified() {
        Set<String> registered = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            if (method.getBeanType().getPackageName().startsWith("hu.reptrack")) {
                for (RequestMethod m : info.getMethodsCondition().getMethods()) {
                    info.getPatternValues().forEach(p -> registered.add(m + " " + p));
                }
            }
        });
        Set<String> classified = new TreeSet<>();
        classified.addAll(PUBLIC);
        classified.addAll(USER);
        classified.addAll(ADMIN);

        assertThat(registered)
                .as("minden végpont legyen besorolva a PUBLIC / USER / ADMIN listák valamelyikébe")
                .isEqualTo(classified);
    }

    // ---- vendég ----

    @ParameterizedTest
    @MethodSource("publicPaths")
    void publicPagesAndStaticFilesAreOpenToGuests(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("protectedGets")
    void guestIsSentToLoginFromEveryProtectedPage(String route) throws Exception {
        mvc.perform(get(path(route))).andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @MethodSource("protectedPosts")
    void guestCannotPerformAnyProtectedAction(String route) throws Exception {
        mvc.perform(post(path(route)).with(csrf())).andExpect(redirectedUrl("/login"));
    }

    // ---- CSRF ----

    @ParameterizedTest
    @MethodSource("allPosts")
    void everyStateChangingRequestNeedsACsrfToken(String route) throws Exception {
        mvc.perform(post(path(route)).with(as(anna))).andExpect(status().isForbidden());
        mvc.perform(post(path(route)).with(as(admin))).andExpect(status().isForbidden());
    }

    @Test
    void formsCarryTheCsrfToken() throws Exception {
        mvc.perform(get("/register")).andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/exercises/new").with(as(anna))).andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    // ---- biztonsági fejlécek ----

    @Test
    void pagesSendSecurityHeaders() throws Exception {
        for (String path : List.of("/login", "/workouts")) {
            mvc.perform(get(path).with(as(anna)))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }
    }
}

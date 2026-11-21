package hu.reptrack;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import hu.reptrack.dto.Role;
import hu.reptrack.service.AuthenticatedUser;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Bejelentkezett felhasználó a MockMvc-tesztekhez, a saját principal típusunkkal ({@link AuthenticatedUser}).
 * A {@code @WithMockUser} principalja nem ilyen, ezért azoknál az oldalaknál, amelyek a felhasználó azonosítóját
 * használják ({@code @AuthenticationPrincipal AuthenticatedUser}), ezt kell használni:
 *
 * <pre>{@code mvc.perform(get("/exercises").with(TestPrincipals.as(anna.getId(), Role.USER)))}</pre>
 *
 * Az azonosító lehet egy adatbázisban nem létező szám is, ha a teszt nem a felhasználó saját adatait vizsgálja.
 */
public final class TestPrincipals {

    private TestPrincipals() {
    }

    public static AuthenticatedUser principal(Long userId, Role role) {
        return new AuthenticatedUser(userId, "teszt" + userId + "@example.com", "Teszt", null, role, true);
    }

    public static RequestPostProcessor as(Long userId, Role role) {
        return user(principal(userId, role));
    }

    /** Adatbázisban nem létező felhasználó: a közös adatokat látja, sajátja nincs. */
    public static RequestPostProcessor anyUser() {
        return as(-1L, Role.USER);
    }

    public static RequestPostProcessor anyAdmin() {
        return as(-2L, Role.ADMIN);
    }
}

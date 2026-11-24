package hu.reptrack.web;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.dto.AccountChangedEvent;
import hu.reptrack.dto.Role;
import hu.reptrack.service.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;

/** Egy fiók munkameneteinek lejáratása (egységteszt, a valódi nyilvántartással). */
class AccountSessionsTest {

    private final SessionRegistry registry = new SessionRegistryImpl();
    private final AccountSessions sessions = new AccountSessions(registry);

    private static AuthenticatedUser principal(long id) {
        return new AuthenticatedUser(id, "u" + id + "@example.com", "U", null, Role.USER, true);
    }

    @Test
    void accountChangeExpiresEverySessionOfThatAccountOnly() {
        registry.registerNewSession("anna-laptop", principal(1));
        registry.registerNewSession("anna-phone", principal(1));
        registry.registerNewSession("bela", principal(2));

        sessions.onAccountChanged(new AccountChangedEvent(1L));

        assertThat(registry.getSessionInformation("anna-laptop").isExpired()).isTrue();
        assertThat(registry.getSessionInformation("anna-phone").isExpired()).isTrue();
        assertThat(registry.getSessionInformation("bela").isExpired()).isFalse();
    }

    @Test
    void theCurrentSessionCanBeKept() {
        registry.registerNewSession("anna-laptop", principal(1));
        registry.registerNewSession("anna-phone", principal(1));

        sessions.expireAllExcept(1L, "anna-phone");

        assertThat(registry.getSessionInformation("anna-laptop").isExpired()).isTrue();
        assertThat(registry.getSessionInformation("anna-phone").isExpired()).isFalse();
    }

    @Test
    void principalsOfTheSameAccountAreEqual() {
        // a jelszócsere után frissített principal is ugyanahhoz a bejegyzéshez tartozik
        assertThat(principal(1)).isEqualTo(principal(1).withPasswordChanged()).hasSameHashCodeAs(principal(1));
        assertThat(principal(1)).isNotEqualTo(principal(2));
    }
}

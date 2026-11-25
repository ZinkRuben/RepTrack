package hu.reptrack.web;

import hu.reptrack.dto.AccountChangedEvent;
import hu.reptrack.service.AuthenticatedUser;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Egy fiók munkameneteinek lejáratása. A Spring Security a bejelentkezett felhasználó adatait a munkamenetben
 * tartja, és kérésenként nem olvassa újra az adatbázisból; letiltás, szerepkör-változás, jelszó-visszaállítás vagy
 * törlés után ezért a fiók munkameneteit le kell járatni. A lejárt munkamenet következő kérése a
 * {@code /login?expired} oldalra kerül (SecurityConfig).
 *
 * <p>A nyilvántartás (SessionRegistry) memóriában van, ami egyetlen alkalmazáspéldánynál (Docker Compose) elég.
 */
@Component
public class AccountSessions {

    private final SessionRegistry registry;

    public AccountSessions(SessionRegistry registry) {
        this.registry = registry;
    }

    /** A szolgáltatásréteg eseménye: csak sikeres (véglegesített) tranzakció után fut le. */
    @TransactionalEventListener
    public void onAccountChanged(AccountChangedEvent event) {
        expireAllExcept(event.userId(), null);
    }

    /**
     * A fiók összes munkamenetének lejáratása, kivéve a megadottat (pl. jelszócserénél a sajátját).
     *
     * @param keepSessionId ez a munkamenet megmarad; {@code null} esetén mind lejár
     */
    public void expireAllExcept(Long userId, String keepSessionId) {
        for (Object principal : registry.getAllPrincipals()) {
            if (principal instanceof AuthenticatedUser user && user.getId().equals(userId)) {
                for (SessionInformation session : registry.getAllSessions(principal, false)) {
                    if (!session.getSessionId().equals(keepSessionId)) {
                        session.expireNow();
                    }
                }
            }
        }
    }
}

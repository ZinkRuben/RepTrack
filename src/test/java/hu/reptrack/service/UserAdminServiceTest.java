package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.User;
import hu.reptrack.dto.AccountChangedEvent;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.StaleDataException;
import hu.reptrack.dto.UserAdminView;
import hu.reptrack.repository.UserRepository;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/** Felhasználók kezelése (UC13): lista, letiltás, szerepkör, jelszó-visszaállítás, törlés és a védőkorlátok. */
@Transactional
@RecordApplicationEvents
@WithMockUser(roles = "ADMIN")
class UserAdminServiceTest extends IntegrationTestBase {

    @Autowired UserAdminService admin;
    @Autowired AccountDetailsService accountDetails;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ApplicationEvents events;

    private User me;
    private User anna;

    @BeforeEach
    void createUsers() {
        me = user("admin@example.com", "Admin", Role.ADMIN);
        anna = user("anna@example.com", "Kiss Anna", Role.USER);
    }

    private User user(String email, String name, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setDisplayName(name);
        u.setPasswordHash(passwordEncoder.encode("Eredeti-jelszo-1"));
        u.setRole(role);
        return users.saveAndFlush(u);
    }

    private User reload(User u) {
        users.flush();
        return users.findById(u.getId()).orElseThrow();
    }

    private List<AccountChangedEvent> changes() {
        return events.stream(AccountChangedEvent.class).toList();
    }

    // ---- lista ----

    @Test
    void listsAccountsAndSearchesByEmailOrName() {
        user("bela@example.com", "Nagy Béla", Role.USER);

        assertThat(admin.list(null, 0).getContent()).extracting(UserAdminView::email)
                .contains("admin@example.com", "anna@example.com", "bela@example.com");
        assertThat(admin.list("KISS", 0).getContent()).extracting(UserAdminView::email)
                .containsExactly("anna@example.com");
        assertThat(admin.list(" bela@ ", 0).getContent()).extracting(UserAdminView::email)
                .containsExactly("bela@example.com");
    }

    @Test
    void theListContainsNoTrainingOrHealthData() {
        // a specifikáció szerint a testtömeg- és pulzusadatot (és az edzésadatot) az adminisztrátor sem láthatja
        assertThat(Arrays.stream(UserAdminView.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("id", "email", "displayName", "role", "enabled", "passwordChangeRequired",
                        "createdAt", "version");
    }

    @Test
    @WithMockUser(roles = "USER")
    void aUserCannotCallTheAdminService() {
        assertThatThrownBy(() -> admin.list(null, 0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> admin.setEnabled(anna.getId(), me.getId(), me.getVersion(), false))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(reload(me).isEnabled()).isTrue();
    }

    // ---- letiltás ----

    @Test
    void disabledAccountCannotLogInAndItsSessionsAreEnded() {
        admin.setEnabled(me.getId(), anna.getId(), anna.getVersion(), false);

        assertThat(reload(anna).isEnabled()).isFalse();
        assertThat(accountDetails.loadUserByUsername("anna@example.com").isEnabled()).isFalse();
        assertThat(changes()).containsExactly(new AccountChangedEvent(anna.getId()));

        admin.setEnabled(me.getId(), anna.getId(), reload(anna).getVersion(), true);
        assertThat(reload(anna).isEnabled()).isTrue();
        assertThat(changes()).as("engedélyezéskor nincs mit lejáratni").hasSize(1);
    }

    @Test
    void staleVersionIsRejected() {
        Long seen = anna.getVersion();
        admin.setEnabled(me.getId(), anna.getId(), seen, false);
        users.flush(); // külön kérésben a verzió már a véglegesített módosításé

        assertThatThrownBy(() -> admin.setEnabled(me.getId(), anna.getId(), seen, true))
                .isInstanceOf(StaleDataException.class);
        assertThatThrownBy(() -> admin.setEnabled(me.getId(), anna.getId(), null, true))
                .isInstanceOf(StaleDataException.class);
        assertThat(reload(anna).isEnabled()).isFalse();
    }

    @Test
    void adminCannotManageTheirOwnAccountHere() {
        assertThatThrownBy(() -> admin.setEnabled(me.getId(), me.getId(), me.getVersion(), false))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> admin.setRole(me.getId(), me.getId(), me.getVersion(), Role.USER))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> admin.resetPassword(me.getId(), me.getId(), me.getVersion()))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> admin.delete(me.getId(), me.getId(), me.getVersion()))
                .isInstanceOf(BusinessRuleException.class);
        User reloaded = reload(me);
        assertThat(reloaded.isEnabled()).isTrue();
        assertThat(reloaded.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void theLastActiveAdminCannotBeDisabledDemotedOrDeleted() {
        // a művelet egy másik (pl. közben letiltott) adminisztrátor munkamenetéből érkezik: "me" az egyetlen aktív
        Long otherAdminId = user("admin2@example.com", "Admin 2", Role.ADMIN).getId();
        User other = users.findById(otherAdminId).orElseThrow();
        other.setEnabled(false);
        users.saveAndFlush(other);

        assertThatThrownBy(() -> admin.setEnabled(otherAdminId, me.getId(), me.getVersion(), false))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("utolsó aktív adminisztrátor");
        assertThatThrownBy(() -> admin.setRole(otherAdminId, me.getId(), me.getVersion(), Role.USER))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> admin.delete(otherAdminId, me.getId(), me.getVersion()))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(reload(me).isEnabled()).isTrue();
        assertThat(changes()).isEmpty();
    }

    @Test
    void anotherAdminCanBeDisabledWhileAnActiveAdminRemains() {
        User second = user("admin2@example.com", "Admin 2", Role.ADMIN);
        admin.setEnabled(me.getId(), second.getId(), second.getVersion(), false);
        assertThat(reload(second).isEnabled()).isFalse();
    }

    // ---- szerepkör ----

    @Test
    void promotesAndDemotesAdmins() {
        admin.setRole(me.getId(), anna.getId(), anna.getVersion(), Role.ADMIN);
        assertThat(reload(anna).getRole()).isEqualTo(Role.ADMIN);

        admin.setRole(me.getId(), anna.getId(), reload(anna).getVersion(), Role.USER);
        assertThat(reload(anna).getRole()).isEqualTo(Role.USER);
        assertThat(changes()).as("mindkét változás után új bejelentkezés kell").hasSize(2);
    }

    // ---- jelszó-visszaállítás ----

    @Test
    void passwordResetSetsARandomTemporaryPasswordThatMustBeChanged() {
        String temporary = admin.resetPassword(me.getId(), anna.getId(), anna.getVersion());

        User reloaded = reload(anna);
        assertThat(temporary).hasSize(14).matches("[a-zA-Z2-9]+");
        assertThat(passwordEncoder.matches(temporary, reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("Eredeti-jelszo-1", reloaded.getPasswordHash())).isFalse();
        assertThat(reloaded.isPasswordChangeRequired()).isTrue();
        assertThat(accountDetails.loadUserByUsername("anna@example.com").isPasswordChangeRequired()).isTrue();
        assertThat(changes()).containsExactly(new AccountChangedEvent(anna.getId()));

        String second = admin.resetPassword(me.getId(), anna.getId(), reloaded.getVersion());
        assertThat(second).as("minden visszaállítás új jelszót ad").isNotEqualTo(temporary);
    }

    // ---- törlés ----

    @Test
    void deletesAnAccount() {
        admin.delete(me.getId(), anna.getId(), anna.getVersion());

        assertThat(users.findById(anna.getId())).isEmpty();
        assertThat(changes()).containsExactly(new AccountChangedEvent(anna.getId()));
    }
}

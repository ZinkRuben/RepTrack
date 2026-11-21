package hu.reptrack.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.User;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.RegistrationRequest;
import hu.reptrack.dto.Role;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.service.AccountDetailsService;
import hu.reptrack.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Az első adminisztrátor létrehozása induláskor. A futtatót közvetlenül hívjuk, tranzakcióban, hogy a létrehozott
 * fiók ne maradjon a többi teszt által is használt adatbázisban.
 */
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class AdminBootstrapTest extends IntegrationTestBase {

    private static final String PASSWORD = "Admin-jelszo-2026";

    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired AccountDetailsService accountDetails;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AdminBootstrap registeredRunner;

    private void runWith(String email, String password, String name) {
        new AdminBootstrap(userService, email, password, name).run(null);
    }

    private long adminCount() {
        return users.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).count();
    }

    @Test
    void isRegisteredAsStartupRunnerAndTestsStartWithoutAdmin() {
        assertThat(registeredRunner).isNotNull();
        assertThat(userService.adminExists()).isFalse();
    }

    @Test
    void createsTheFirstAdminFromTheConfiguration(CapturedOutput output) {
        runWith(" Admin@RepTrack.test ", PASSWORD, "Első Admin");

        User admin = users.findByEmailIgnoreCase("admin@reptrack.test").orElseThrow();
        assertThat(admin.getEmail()).isEqualTo("admin@reptrack.test");
        assertThat(admin.getDisplayName()).isEqualTo("Első Admin");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
        assertThat(AuthorityUtils.authorityListToSet(accountDetails.loadUserByUsername("admin@reptrack.test")
                .getAuthorities())).containsExactly("ROLE_ADMIN");

        assertThat(output).contains("Adminisztrátori fiók létrehozva: admin@reptrack.test")
                .doesNotContain(PASSWORD);
    }

    @Test
    void doesNothingWhenAnAdminAlreadyExists() {
        runWith("admin@reptrack.test", PASSWORD, "Első Admin");
        String originalHash = users.findByEmailIgnoreCase("admin@reptrack.test").orElseThrow().getPasswordHash();

        // újraindítás más adatokkal: nem jön létre második admin, és a meglévő jelszava sem változik
        runWith("masik@reptrack.test", "Masik-jelszo-2026", "Második");
        runWith("admin@reptrack.test", "Uj-jelszo-2026", "Első Admin");

        assertThat(adminCount()).isEqualTo(1);
        assertThat(users.findByEmailIgnoreCase("masik@reptrack.test")).isEmpty();
        assertThat(users.findByEmailIgnoreCase("admin@reptrack.test").orElseThrow().getPasswordHash())
                .isEqualTo(originalHash);
    }

    @Test
    void onlyWarnsWhenNothingIsConfigured(CapturedOutput output) {
        runWith("", "", "Adminisztrátor");

        assertThat(adminCount()).isZero();
        assertThat(output).contains("Nincs adminisztrátori fiók", "REPTRACK_ADMIN_EMAIL", "REPTRACK_ADMIN_PASSWORD");
    }

    @Test
    void stopsStartupWhenOnlyOneOfEmailAndPasswordIsGiven() {
        assertThatThrownBy(() -> runWith("admin@reptrack.test", "", "Adminisztrátor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("csak az egyik van megadva");
        assertThatThrownBy(() -> runWith(" ", PASSWORD, "Adminisztrátor"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(adminCount()).isZero();
    }

    @Test
    void stopsStartupOnInvalidValuesAndNamesTheVariableToFix() {
        assertThatThrownBy(() -> runWith("admin@reptrack.test", "rovid", "Adminisztrátor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REPTRACK_ADMIN_PASSWORD: A jelszó legalább 8 karakter legyen.")
                .message().doesNotContain("rovid");
        assertThatThrownBy(() -> runWith("nem-email", PASSWORD, "Adminisztrátor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REPTRACK_ADMIN_EMAIL: Érvénytelen e-mail-cím.");
        assertThat(adminCount()).isZero();
    }

    @Test
    void neverPromotesAnExistingAccount() {
        userService.register(new RegistrationRequest("Teszt Anna", "anna@example.com", "anna-jelszava", "anna-jelszava"));
        String annasHash = users.findByEmailIgnoreCase("anna@example.com").orElseThrow().getPasswordHash();

        assertThatThrownBy(() -> runWith("ANNA@example.com", PASSWORD, "Adminisztrátor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("már egy meglévő, nem adminisztrátor fiókhoz tartozik");

        User anna = users.findByEmailIgnoreCase("anna@example.com").orElseThrow();
        assertThat(anna.getRole()).isEqualTo(Role.USER);
        assertThat(anna.getPasswordHash()).isEqualTo(annasHash);
        assertThat(adminCount()).isZero();
    }

    @Test
    void serviceRefusesASecondInitialAdmin() {
        runWith("admin@reptrack.test", PASSWORD, "Első Admin");
        assertThatThrownBy(() -> userService.createInitialAdmin(
                new RegistrationRequest("Második", "masik@reptrack.test", PASSWORD, PASSWORD)))
                .isInstanceOf(BusinessRuleException.class);
    }
}

package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.User;
import hu.reptrack.dto.DuplicateEmailException;
import hu.reptrack.dto.RegistrationRequest;
import hu.reptrack.dto.Role;
import hu.reptrack.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Regisztráció (UserService) és a felhasználók betöltése bejelentkezéshez (AccountDetailsService). */
@Transactional
class UserServiceTest extends IntegrationTestBase {

    @Autowired UserService userService;
    @Autowired AccountDetailsService accountDetails;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    private static RegistrationRequest request(String name, String email, String password, String confirm) {
        return new RegistrationRequest(name, email, password, confirm);
    }

    private static RegistrationRequest valid(String email) {
        return request("Teszt Anna", email, "titkos123", "titkos123");
    }

    /** A megsértett szabályok (mező/tulajdonság nevei) egy érvénytelen kérésnél. */
    private String[] violatedProperties(RegistrationRequest request) {
        try {
            userService.register(request);
        } catch (ConstraintViolationException e) {
            return e.getConstraintViolations().stream()
                    .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                    .map(path -> path.substring(path.lastIndexOf('.') + 1))
                    .sorted().toArray(String[]::new);
        }
        throw new AssertionError("ConstraintViolationException-t vártunk");
    }

    // ---- regisztráció ----

    @Test
    void registersUserWithHashedPasswordAndUserRole() {
        Long id = userService.register(valid("anna@example.com"));

        User saved = users.findById(id).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("anna@example.com");
        assertThat(saved.getDisplayName()).isEqualTo("Teszt Anna");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("titkos123").startsWith("$2");
        assertThat(passwordEncoder.matches("titkos123", saved.getPasswordHash())).isTrue();
    }

    @Test
    void emailIsTrimmedAndStoredInLowerCase() {
        Long id = userService.register(valid("  Anna.Kiss@Example.COM "));
        assertThat(users.findById(id).orElseThrow().getEmail()).isEqualTo("anna.kiss@example.com");
    }

    @Test
    void duplicateEmailIsRejectedRegardlessOfCase() {
        userService.register(valid("anna@example.com"));

        assertThatThrownBy(() -> userService.register(valid("ANNA@example.com")))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Ezzel az e-mail-címmel már regisztráltak.");
    }

    @Test
    void validationRulesAreEnforcedInTheServiceToo() {
        assertThat(violatedProperties(request("", "anna@example.com", "titkos123", "titkos123")))
                .containsExactly("displayName");
        assertThat(violatedProperties(request("Anna", "nem-email", "titkos123", "titkos123")))
                .containsExactly("email");
        assertThat(violatedProperties(request("Anna", "anna@localhost", "titkos123", "titkos123")))
                .as("pont nélküli domain").containsExactly("email");
        assertThat(violatedProperties(request("Anna", "anna@example.com", "rovid", "rovid")))
                .containsExactly("password");
        assertThat(violatedProperties(request("Anna", "anna@example.com", "titkos123", "titkos124")))
                .containsExactly("passwordConfirmed");
        String tooManyBytes = "á".repeat(37); // 37 karakter, de 74 bájt
        assertThat(violatedProperties(request("Anna", "anna@example.com", tooManyBytes, tooManyBytes)))
                .containsExactly("passwordWithinLimit");
        assertThat(users.findByEmailIgnoreCase("anna@example.com")).isEmpty();
    }

    // ---- betöltés bejelentkezéshez ----

    @Test
    void loadsRegisteredUserByEmailInAnyCase() {
        Long id = userService.register(valid("anna@example.com"));

        AuthenticatedUser loaded = accountDetails.loadUserByUsername(" Anna@Example.com ");
        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getUsername()).isEqualTo("anna@example.com");
        assertThat(loaded.getDisplayName()).isEqualTo("Teszt Anna");
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(AuthorityUtils.authorityListToSet(loaded.getAuthorities())).containsExactly("ROLE_USER");
        assertThat(passwordEncoder.matches("titkos123", loaded.getPassword())).isTrue();

        loaded.eraseCredentials();
        assertThat(loaded.getPassword()).as("a hash nem marad a munkamenetben").isNull();
    }

    @Test
    void adminGetsAdminAuthorityAndDisabledFlagIsPassedOn() {
        User admin = users.findById(userService.register(valid("admin@example.com"))).orElseThrow();
        admin.setRole(Role.ADMIN);
        admin.setEnabled(false);
        users.flush();

        AuthenticatedUser loaded = accountDetails.loadUserByUsername("admin@example.com");
        assertThat(AuthorityUtils.authorityListToSet(loaded.getAuthorities())).containsExactly("ROLE_ADMIN");
        assertThat(loaded.isEnabled()).isFalse();
    }

    @Test
    void unknownEmailIsNotFound() {
        assertThatThrownBy(() -> accountDetails.loadUserByUsername("senki@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}

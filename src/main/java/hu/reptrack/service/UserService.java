package hu.reptrack.service;

import hu.reptrack.domain.User;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.DuplicateEmailException;
import hu.reptrack.dto.RegistrationRequest;
import hu.reptrack.dto.Role;
import hu.reptrack.repository.UserRepository;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * Felhasználói fiókok (UC1): regisztráció és az első adminisztrátor létrehozása. A bejelentkezést a Spring Security
 * végzi az {@link AccountDetailsService}-en keresztül.
 */
@Service
@Validated
@Transactional
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Új, USER szerepkörű fiók létrehozása. Az e-mail-cím kisbetűsen tárolódik, és egyedinek kell lennie
     * (kis- és nagybetűtől függetlenül); a jelszó BCrypt-hash-ként kerül az adatbázisba.
     *
     * @return az új felhasználó azonosítója
     * @throws DuplicateEmailException ha a cím már foglalt
     */
    public Long register(@Valid RegistrationRequest request) {
        return createAccount(request, Role.USER);
    }

    @Transactional(readOnly = true)
    public boolean adminExists() {
        return users.existsByRole(Role.ADMIN);
    }

    /**
     * Az első adminisztrátori fiók létrehozása telepítéskor (AdminBootstrap). Csak akkor működik, ha még nincs
     * adminisztrátor, így nem használható újabb admin létrehozására. Ugyanazok a szabályok érvényesek, mint a
     * regisztrációnál; meglévő fiókot nem léptet elő és nem ír felül.
     *
     * @return az új adminisztrátor azonosítója
     * @throws BusinessRuleException ha már van adminisztrátor
     * @throws DuplicateEmailException ha a cím egy meglévő (nem adminisztrátor) fiókhoz tartozik
     */
    public Long createInitialAdmin(@Valid RegistrationRequest request) {
        if (adminExists()) {
            throw new BusinessRuleException("Már van adminisztrátori fiók.");
        }
        return createAccount(request, Role.ADMIN);
    }

    private Long createAccount(RegistrationRequest request, Role role) {
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException();
        }

        User user = new User();
        user.setEmail(email);
        user.setDisplayName(request.getDisplayName().strip());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        try {
            return users.saveAndFlush(user).getId();
        } catch (DataIntegrityViolationException e) {
            // két egyidejű regisztráció ugyanazzal a címmel: a második az egyedi indexen akad el
            throw new DuplicateEmailException();
        }
    }
}

package hu.reptrack.service;

import hu.reptrack.domain.User;
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
 * Felhasználói fiókok (UC1): regisztráció. A bejelentkezést a Spring Security végzi az
 * {@link AccountDetailsService}-en keresztül.
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
        String email = request.getEmail().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException();
        }

        User user = new User();
        user.setEmail(email);
        user.setDisplayName(request.getDisplayName().strip());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        try {
            return users.saveAndFlush(user).getId();
        } catch (DataIntegrityViolationException e) {
            // két egyidejű regisztráció ugyanazzal a címmel: a második az egyedi indexen akad el
            throw new DuplicateEmailException();
        }
    }
}

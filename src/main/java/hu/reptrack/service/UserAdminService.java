package hu.reptrack.service;

import hu.reptrack.domain.User;
import hu.reptrack.dto.AccountChangedEvent;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.StaleDataException;
import hu.reptrack.dto.UserAdminView;
import hu.reptrack.repository.UserRepository;
import java.security.SecureRandom;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Felhasználók kezelése (UC13): fióklista, letiltás és engedélyezés, jelszó-visszaállítás, adminisztrátor
 * kinevezése és visszavonása, fiók törlése. Minden metódust metódusszintű jogosultság is véd (az URL-szabályon túl).
 *
 * <p>Szabályok:
 * <ul>
 *   <li>Az adminisztrátor a saját fiókján itt nem végezhet műveletet (a saját jelszavát és fiókját a profilból
 *       kezeli), így véletlenül sem zárhatja ki magát.</li>
 *   <li>Az utolsó aktív adminisztrátort senki nem tilthatja le, minősítheti vissza vagy törölheti.</li>
 *   <li>Optimista zárolás: a művelet a listában látott verzióra vonatkozik; ha közben más módosította a fiókot,
 *       {@link StaleDataException}.</li>
 *   <li>Minden korlátozó változás után a fiók munkamenetei lejárnak ({@link AccountChangedEvent}): a letiltott
 *       felhasználó azonnal kikerül, az új szerepkör a következő bejelentkezéstől él.</li>
 * </ul>
 * Az adminisztrátor a fiókok edzés- és egészségügyi adatait nem látja ({@link UserAdminView}).
 */
@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminService {

    public static final int PAGE_SIZE = 20;

    /** Ideiglenes jelszó: összetéveszthető karakterek (0/O, 1/l/I) nélkül, hogy diktálni is lehessen. */
    private static final String TEMP_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TEMP_LENGTH = 14;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public UserAdminService(UserRepository users, PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    /** Lapozott fióklista e-mail-cím szerint rendezve; a {@code query} az e-mail-címben vagy a névben keres. */
    @Transactional(readOnly = true)
    public Page<UserAdminView> list(String query, int page) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("email"));
        String q = query == null ? "" : query.strip();
        Page<User> result = q.isEmpty()
                ? users.findAll(pageable)
                : users.findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(q, q, pageable);
        return result.map(UserAdminService::toView);
    }

    @Transactional(readOnly = true)
    public UserAdminView get(Long userId) {
        return toView(load(userId));
    }

    /** Letiltás ({@code enabled = false}) vagy újraengedélyezés. A letiltott fiók nem tud bejelentkezni. */
    public void setEnabled(Long adminId, Long userId, Long version, boolean enabled) {
        User user = loadForChange(adminId, userId, version);
        if (user.isEnabled() == enabled) {
            return;
        }
        if (!enabled && AdminSafeguard.isLastActiveAdmin(users, user)) {
            throw new BusinessRuleException("Az utolsó aktív adminisztrátor nem tiltható le.");
        }
        user.setEnabled(enabled);
        if (!enabled) {
            events.publishEvent(new AccountChangedEvent(userId));
        }
    }

    /** Adminisztrátor kinevezése ({@link Role#ADMIN}) vagy visszaminősítése felhasználóvá ({@link Role#USER}). */
    public void setRole(Long adminId, Long userId, Long version, Role role) {
        User user = loadForChange(adminId, userId, version);
        if (user.getRole() == role) {
            return;
        }
        if (role == Role.USER && AdminSafeguard.isLastActiveAdmin(users, user)) {
            throw new BusinessRuleException("Az utolsó aktív adminisztrátor nem minősíthető vissza.");
        }
        user.setRole(role);
        events.publishEvent(new AccountChangedEvent(userId));
    }

    /**
     * Jelszó-visszaállítás: véletlen ideiglenes jelszó, amelyet a felhasználónak a következő bejelentkezéskor le kell
     * cserélnie. Az ideiglenes jelszó csak itt, a visszatérési értékben létezik (az adatbázisba hash kerül), az
     * adminisztrátor egyszer látja, és ő adja át a felhasználónak.
     *
     * @return az ideiglenes jelszó
     */
    public String resetPassword(Long adminId, Long userId, Long version) {
        User user = loadForChange(adminId, userId, version);
        String temporary = temporaryPassword();
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setPasswordChangeRequired(true);
        events.publishEvent(new AccountChangedEvent(userId));
        return temporary;
    }

    /** A fiók végleges törlése minden adatával (ugyanaz a kaszkádolt törlés, mint a saját fióknál, UC14). */
    public void delete(Long adminId, Long userId, Long version) {
        User user = loadForChange(adminId, userId, version);
        if (AdminSafeguard.isLastActiveAdmin(users, user)) {
            throw new BusinessRuleException("Az utolsó aktív adminisztrátor nem törölhető.");
        }
        users.deleteAccountWithAllData(user.getId());
        events.publishEvent(new AccountChangedEvent(userId));
    }

    // ---- segédek ----

    private User load(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("A fiók nem található."));
    }

    private User loadForChange(Long adminId, Long userId, Long version) {
        if (userId.equals(adminId)) {
            throw new BusinessRuleException("A saját fiókodat itt nem módosíthatod; a jelszavad és a fiókod "
                    + "törlése a profilodban érhető el.");
        }
        User user = load(userId);
        if (version == null || !version.equals(user.getVersion())) {
            throw new StaleDataException("A fiókot közben módosították. Nézd meg a frissített listát, és próbáld újra.");
        }
        return user;
    }

    private String temporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_LENGTH);
        for (int i = 0; i < TEMP_LENGTH; i++) {
            sb.append(TEMP_ALPHABET.charAt(random.nextInt(TEMP_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static UserAdminView toView(User u) {
        return new UserAdminView(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.isEnabled(),
                u.isPasswordChangeRequired(), u.getCreatedAt(), u.getVersion());
    }
}

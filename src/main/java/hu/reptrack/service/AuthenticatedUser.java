package hu.reptrack.service;

import hu.reptrack.dto.Role;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * A bejelentkezett felhasználó a munkamenetben. A controllerek így kapják meg a szolgáltatásoknak átadandó
 * azonosítót:
 *
 * <pre>{@code
 * @GetMapping("/workouts")
 * public String list(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
 *     model.addAttribute("workouts", workoutService.list(user.getId(), ...));
 * }
 * }</pre>
 *
 * A felhasználónév az e-mail-cím. Egyetlen jogosultsága a szerepköre ({@code ROLE_USER} vagy {@code ROLE_ADMIN});
 * az ADMIN a szerepkör-hierarchia (SecurityConfig) révén a USER jogait is megkapja. A jelszó-hash a sikeres
 * bejelentkezés után törlődik, nem marad a munkamenetben.
 *
 * <p>Két principal akkor egyenlő, ha ugyanahhoz a fiókhoz tartozik (azonosító): a munkamenet-nyilvántartás
 * (SessionRegistry) így találja meg egy fiók összes munkamenetét, például letiltáskor.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String email;
    private final String displayName;
    private final Role role;
    private final boolean enabled;
    private final boolean passwordChangeRequired;
    private String passwordHash;

    public AuthenticatedUser(Long id, String email, String displayName, String passwordHash, Role role,
                             boolean enabled) {
        this(id, email, displayName, passwordHash, role, enabled, false);
    }

    /** @param passwordChangeRequired ideiglenes jelszóval lépett be: amíg nem cserél jelszót, mást nem érhet el */
    public AuthenticatedUser(Long id, String email, String displayName, String passwordHash, Role role,
                             boolean enabled, boolean passwordChangeRequired) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    /** Ugyanez a felhasználó a jelszócsere után (a munkamenetben lévő principal frissítéséhez). */
    public AuthenticatedUser withPasswordChanged() {
        return new AuthenticatedUser(id, email, displayName, null, role, enabled, false);
    }

    public Long getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AuthenticatedUser other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}

package hu.reptrack.service;

import hu.reptrack.domain.User;
import hu.reptrack.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Spring Security ezen keresztül tölti be a bejelentkező felhasználót a users táblából (e-mail-cím alapján,
 * kis- és nagybetűtől függetlenül). A letiltott fiók ({@code enabled = false}) nem tud bejelentkezni.
 */
@Service
@Transactional(readOnly = true)
public class AccountDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AccountDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public AuthenticatedUser loadUserByUsername(String email) {
        String normalized = email == null ? "" : email.strip();
        User user = users.findByEmailIgnoreCase(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Nincs ilyen felhasználó."));
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getPasswordHash(),
                user.getRole(), user.isEnabled(), user.isPasswordChangeRequired());
    }
}

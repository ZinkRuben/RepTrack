package hu.reptrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Biztonsági beállítások.
 *
 * <ul>
 *   <li>Nyilvános: a nyitóoldal, a bejelentkezés, a regisztráció, a hibaoldal és a statikus fájlok.</li>
 *   <li>Minden más oldal USER szerepkört kér, az {@code /admin/**} oldalak ADMIN-t.</li>
 *   <li>Szerepkör-hierarchia: ADMIN &gt; USER, vagyis az adminisztrátor a felhasználói funkciókat is eléri. A hierarchia
 *       az URL-szabályokra, a metódusszintű jogosultságra ({@code @PreAuthorize} a szolgáltatásokban) és a
 *       sablonok {@code sec:authorize} feltételeire egyaránt érvényes.</li>
 *   <li>Űrlapos bejelentkezés e-mail-címmel a saját /login oldalon; a felhasználókat az AccountDetailsService tölti
 *       be az adatbázisból, a jelszavak BCrypt-hash-ként tárolódnak.</li>
 *   <li>CSRF-védelem bekapcsolva (alapértelmezés): a {@code th:action}-nel készült űrlapok automatikusan kapják a
 *       tokent, a kijelentkezés is csak POST-tal, tokennel működik.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/error",
                        "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().hasRole("USER"))
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll());
        return http.build();
    }

    /** ADMIN &gt; USER: az ADMIN szerepkörű felhasználó minden USER-jogosultságot is megkap. */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("USER")
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

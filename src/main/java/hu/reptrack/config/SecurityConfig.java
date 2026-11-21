package hu.reptrack.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
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
 *   <li>HTTPS esetén (server.ssl.enabled=true, pl. a docker profil) minden sima HTTP-kérés HTTPS-re irányítódik
 *       (a HTTP-portot a HttpsConfig nyitja). Az átirányítás a gazdagépen látható portokra mutat
 *       (reptrack.https.public-http-port → public-https-port). A HSTS fejléc a reptrack.https.hsts beállítással
 *       kapcsolható ki (önaláírt tanúsítványnál a localhost-on ki van kapcsolva).</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    @Value("${server.ssl.enabled:false}") boolean sslEnabled,
                                    @Value("${reptrack.https.http-port:8080}") int httpPort,
                                    @Value("${reptrack.https.public-http-port:8080}") int publicHttpPort,
                                    @Value("${reptrack.https.public-https-port:8443}") int publicHttpsPort,
                                    @Value("${reptrack.https.hsts:true}") boolean hsts) throws Exception {
        if (sslEnabled) {
            http
                .portMapper(ports -> ports
                    .http(httpPort).mapsTo(publicHttpsPort)
                    .http(publicHttpPort).mapsTo(publicHttpsPort))
                .redirectToHttps(Customizer.withDefaults());
        }
        if (!hsts) {
            http.headers(headers -> headers.httpStrictTransportSecurity(hstsConfig -> hstsConfig.disable()));
        }
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

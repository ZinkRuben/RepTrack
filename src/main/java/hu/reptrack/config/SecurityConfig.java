package hu.reptrack.config;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Biztonsági beállítások.
 *
 * <ul>
 *   <li>Nyilvános: a nyitóoldal, a bejelentkezés, a regisztráció, a hibaoldal és a statikus fájlok (a WebJarként
 *       csomagolt könyvtárak is, {@code /webjars/**}).</li>
 *   <li>Minden más oldal USER szerepkört kér, az {@code /admin/**} oldalak ADMIN-t.</li>
 *   <li>Szerepkör-hierarchia: ADMIN &gt; USER, vagyis az adminisztrátor a felhasználói funkciókat is eléri. A hierarchia
 *       az URL-szabályokra, a metódusszintű jogosultságra ({@code @PreAuthorize} a szolgáltatásokban) és a
 *       sablonok {@code sec:authorize} feltételeire egyaránt érvényes.</li>
 *   <li>Űrlapos bejelentkezés e-mail-címmel a saját /login oldalon; a felhasználókat az AccountDetailsService tölti
 *       be az adatbázisból, a jelszavak BCrypt-hash-ként tárolódnak.</li>
 *   <li>Bejelentkezés után a felhasználó oda kerül, ahová
 *       oldalt akart megnyitni; a böngésző háttérkéréseit nem jegyezzük meg ({@link #pageRequestCache()}).</li>
 *   <li>Munkamenet-nyilvántartás (SessionRegistry): egy fiók munkamenetei lejárathatók (letiltás, szerepkör-változás,
 *       jelszó-visszaállítás, törlés; web.AccountSessions). A lejárt munkamenet a /login?expired oldalra kerül.</li>
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
                        "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                // a böngészők és keresők háttérkérései (nálunk nincsenek ilyen fájlok): azonnal 404, nem a
                // bejelentkezésre irányítanak, így bejelentkezés után sem ezekre kerül a felhasználó
                .requestMatchers(BACKGROUND_REQUESTS).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().hasRole("USER"))
            .requestCache(cache -> cache.requestCache(pageRequestCache()))
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .failureUrl("/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll())
            .sessionManagement(session -> session
                .maximumSessions(-1)
                .sessionRegistry(sessionRegistry())
                .expiredUrl("/login?expired"));
        return http.build();
    }

    /** A bejelentkezett felhasználók munkamenetei (korlátlan számú munkamenet fiókonként). */
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** A lejárt vagy kijelentkezett munkamenetek kikerülnek a nyilvántartásból. */
    @Bean
    static HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * Ezeket a böngésző vagy egy kereső magától kéri, a felhasználó nem látja őket (pl. a Chrome fejlesztői eszközei a
     * {@code /.well-known/…} alatt, a Safari az {@code apple-touch-icon} képet). Az alkalmazásban nincsenek ilyen
     * fájlok, és adatot sem tartalmaznak.
     */
    static final String[] BACKGROUND_REQUESTS = {
        "/.well-known/**", "/robots.txt", "/favicon.ico", "/apple-touch-icon*.png", "/sitemap.xml"
    };

    /**
     * A bejelentkezés után csak oda irányítunk vissza, ahová a felhasználó valóban oldalt akart megnyitni: a
     * megjegyzett kérés GET, HTML-t kér (a böngésző navigációja így küldi), és nem háttérben futó szkriptkérés. A kép-,
     * adat- és egyéb háttérkéréseket nem jegyezzük meg, különben bejelentkezés után egy nem létező fájlra (404)
     * kerülne a felhasználó.
     */
    static RequestCache pageRequestCache() {
        MediaTypeRequestMatcher acceptsHtml = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        acceptsHtml.setIgnoredMediaTypes(Set.of(MediaType.ALL));     // a „*/*” még nem jelent oldalkérést
        RequestMatcher get = request -> "GET".equals(request.getMethod());
        RequestMatcher notScript = new NegatedRequestMatcher(new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest"));
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(new AndRequestMatcher(get, acceptsHtml, notScript));
        return cache;
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

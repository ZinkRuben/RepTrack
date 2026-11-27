package hu.reptrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.User;
import hu.reptrack.dto.Role;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.service.AuthenticatedUser;
import hu.reptrack.service.UserAdminService;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * A fiókkezelés hatása a már bejelentkezett felhasználóra, valódi Tomcat-tal és munkamenetekkel (a MockMvc nem
 * használja a munkamenet-nyilvántartást): letiltás után a meglévő munkamenet azonnal megszűnik és a belépés is
 * tilos; jelszó-visszaállítás után az ideiglenes jelszóval csak a jelszócsere érhető el.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"reptrack.admin.email=", "reptrack.admin.password=", "reptrack.demo.enabled=false"})
class AccountLifecycleHttpTest extends IntegrationTestBase {

    private static final String EMAIL = "eletciklus@example.com";
    private static final String ADMIN_EMAIL = "eletciklus-admin@example.com";
    private static final String PASSWORD = "Eletciklus-teszt-1";
    private static final Pattern SESSION = Pattern.compile("JSESSIONID=([^;]+)");
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" (?:type=\"hidden\" )?value=\"([^\"]+)\"");

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired UserAdminService userAdmin;
    @Autowired PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private User user;
    private User admin;

    // a valódi szerver külön szálon fut, ezért a fiókok tényleg bekerülnek az adatbázisba (és utána törlődnek)
    @BeforeEach
    void createAccounts() {
        user = account(EMAIL, Role.USER);
        admin = account(ADMIN_EMAIL, Role.ADMIN);
    }

    @AfterEach
    void deleteAccounts() {
        for (String email : new String[] {EMAIL, ADMIN_EMAIL}) {
            users.findByEmailIgnoreCase(email).ifPresent(users::delete);
        }
    }

    private User account(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setDisplayName("Életciklus");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRole(role);
        return users.save(u);
    }

    /** A szolgáltatás hívása az adminisztrátor nevében (saját tranzakcióban, így a véglegesítés utáni események is lefutnak). */
    private <T> T asAdmin(Supplier<T> action) {
        AuthenticatedUser principal = new AuthenticatedUser(admin.getId(), ADMIN_EMAIL, "Admin", null, Role.ADMIN, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Long currentVersion() {
        return users.findById(user.getId()).orElseThrow().getVersion();
    }

    // ---- HTTP-segédek ----

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> get(String path, String sessionId) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).header("Cookie", "JSESSIONID=" + sessionId).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String sessionId, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path))
                        .header("Cookie", "JSESSIONID=" + sessionId)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String find(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        assertThat(m.find()).as("nem található: %s", pattern).isTrue();
        return m.group(1);
    }

    private static String form(String... pairs) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) {
            body.append(i == 0 ? "" : "&").append(pairs[i]).append('=')
                    .append(URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private static String location(HttpResponse<?> response) {
        return response.headers().firstValue("Location").orElse("");
    }

    /** Bejelentkezés; a válasz és a munkamenet-azonosító (sikeres belépéskor az új, egyébként a régi). */
    private String[] login(String password) throws Exception {
        HttpResponse<String> page = http.send(HttpRequest.newBuilder(uri("/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String session = find(SESSION, page.headers().firstValue("Set-Cookie").orElse(""));
        HttpResponse<String> login = post("/login", session,
                form("email", EMAIL, "password", password, "_csrf", find(CSRF, page.body())));
        String cookie = login.headers().firstValue("Set-Cookie").orElse("");
        String after = SESSION.matcher(cookie).find() ? find(SESSION, cookie) : session;
        return new String[] {location(login), after};
    }

    // ---- tesztek ----

    @Test
    void disablingEndsTheExistingSessionAndBlocksLogin() throws Exception {
        String session = login(PASSWORD)[1];
        assertThat(get("/workouts", session).statusCode()).isEqualTo(200);

        asAdmin(() -> {
            userAdmin.setEnabled(admin.getId(), user.getId(), currentVersion(), false);
            return null;
        });

        HttpResponse<String> after = get("/workouts", session);
        assertThat(after.statusCode()).as("a meglévő munkamenet lejárt").isEqualTo(302);
        assertThat(location(after)).endsWith("/login?expired");
        assertThat(get("/login?expired", session).body()).contains("A munkameneted lejárt");

        String[] again = login(PASSWORD);
        assertThat(again[0]).as("letiltva nem lehet belépni").endsWith("/login?error");
        assertThat(get("/login?error", again[1]).body()).contains("A fiókodat letiltották");
    }

    @Test
    void temporaryPasswordOnlyOpensThePasswordChange() throws Exception {
        String oldSession = login(PASSWORD)[1];
        String temporary = asAdmin(() -> userAdmin.resetPassword(admin.getId(), user.getId(), currentVersion()));

        assertThat(location(get("/workouts", oldSession))).as("a régi munkamenet lejárt").endsWith("/login?expired");
        assertThat(login(PASSWORD)[0]).as("a régi jelszó már nem jó").endsWith("/login?error");

        String session = login(temporary)[1];
        assertThat(location(get("/workouts", session))).endsWith("/account/password");

        HttpResponse<String> page = get("/account/password", session);
        assertThat(page.statusCode()).isEqualTo(200);
        HttpResponse<String> change = post("/account/password", session, form("currentPassword", temporary,
                "newPassword", "Sajat-uj-jelszo-1", "newPasswordConfirm", "Sajat-uj-jelszo-1",
                "_csrf", find(CSRF, page.body())));
        assertThat(location(change)).endsWith("/");

        assertThat(get("/workouts", session).statusCode()).as("a csere után ugyanabban a munkamenetben tovább dolgozhat")
                .isEqualTo(200);
        assertThat(users.findById(user.getId()).orElseThrow().isPasswordChangeRequired()).isFalse();
    }
}

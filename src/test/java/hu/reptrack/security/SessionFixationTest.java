package hu.reptrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.dto.RegistrationRequest;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.service.UserService;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Session fixation elleni védelem valódi Tomcat-tal (a MockMvc nem cseréli a munkamenet-azonosítót, ezért itt
 * HTTP-n át, a JDK HTTP-kliensével tesztelünk): a bejelentkezés új munkamenet-azonosítót ad, és a régi azonosító
 * nem jelentkezik be. Így egy támadó által előre "elültetett" munkamenet a bejelentkezés után sem használható.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"reptrack.admin.email=", "reptrack.admin.password="})
class SessionFixationTest extends IntegrationTestBase {

    private static final String EMAIL = "session-fixation@example.com";
    private static final String PASSWORD = "Munkamenet-teszt-1";
    private static final Pattern SESSION = Pattern.compile("JSESSIONID=([^;]+)");
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" (?:type=\"hidden\" )?value=\"([^\"]+)\"");

    @LocalServerPort int port;
    @Autowired UserService userService;
    @Autowired UserRepository users;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    // a valódi szerver külön szálon fut, ezért a felhasználó tényleg bekerül az adatbázisba (és utána törlődik)
    @BeforeEach
    void registerUser() {
        userService.register(new RegistrationRequest("Munkamenet Teszt", EMAIL, PASSWORD, PASSWORD));
    }

    @AfterEach
    void deleteUser() {
        users.findByEmailIgnoreCase(EMAIL).ifPresent(users::delete);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> get(String path, String sessionId) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).header("Cookie", "JSESSIONID=" + sessionId).GET().build(),
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

    @Test
    void loginIssuesANewSessionIdAndTheOldOneStaysLoggedOut() throws Exception {
        HttpResponse<String> loginPage = http.send(HttpRequest.newBuilder(uri("/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String before = find(SESSION, loginPage.headers().firstValue("Set-Cookie").orElse(""));
        String csrf = find(CSRF, loginPage.body());

        HttpResponse<String> login = http.send(HttpRequest.newBuilder(uri("/login"))
                        .header("Cookie", "JSESSIONID=" + before)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form("email", EMAIL, "password", PASSWORD, "_csrf", csrf)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("Location").orElse("")).doesNotContain("error");
        String after = find(SESSION, login.headers().firstValue("Set-Cookie").orElse(""));

        assertThat(after).as("új munkamenet-azonosító a bejelentkezés után").isNotEqualTo(before);
        assertThat(get("/workouts", after).statusCode()).as("az új azonosító be van jelentkezve").isEqualTo(200);
        HttpResponse<String> old = get("/workouts", before);
        assertThat(old.statusCode()).as("a régi azonosító nem jelentkezik be").isEqualTo(302);
        assertThat(old.headers().firstValue("Location").orElse("")).endsWith("/login");
    }
}

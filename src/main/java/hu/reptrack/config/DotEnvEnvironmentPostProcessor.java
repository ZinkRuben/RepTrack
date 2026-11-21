package hu.reptrack.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * A munkakönyvtár .env fájljának betöltése, úgy, mintha környezeti változók lennének (ugyanazt a fájlt a Docker
 * Compose is olvassa). Így helyben elég a .env-be írni pl. a REPTRACK_ADMIN_PASSWORD-öt, a repóba nem kerül.
 *
 * <ul>
 *   <li>UTF-8 kódolású (a Spring Boot a .properties fájlokat ISO-8859-1-ként olvasná, az ékezetek elromlanának).</li>
 *   <li>A valódi környezeti változók felülírják, az application.properties értékeit viszont ez írja felül.</li>
 *   <li>Formátum: {@code KULCS=érték} soronként; üres sor és {@code #}-tel kezdődő megjegyzés megengedett, az
 *       {@code export } előtag és az értéket körülvevő idézőjelek elhagyhatók.</li>
 *   <li>Ha nincs .env fájl, nem történik semmi.</li>
 * </ul>
 *
 * Regisztráció: META-INF/spring.factories.
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** A forrás neve "-systemEnvironment"-re végződik: így a Spring a környezeti változók szabályaival köti
     *  (pl. SERVER_PORT → server.port). */
    static final String PROPERTY_SOURCE_NAME = "dotenvFile-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

    /** A konfigurációs fájlok (application.properties) feldolgozása előtt fut, mint a valódi környezeti változók. */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 5;

    private final Path file;

    public DotEnvEnvironmentPostProcessor() {
        this(Path.of(".env"));
    }

    DotEnvEnvironmentPostProcessor(Path file) {
        this.file = file;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Map<String, Object> values;
        try {
            values = parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("A " + file + " fájl nem olvasható.", e);
        }
        SystemEnvironmentPropertySource source = new SystemEnvironmentPropertySource(PROPERTY_SOURCE_NAME, values);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            environment.getPropertySources().addLast(source);
        }
    }

    static Map<String, Object> parse(List<String> lines) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).strip();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).strip();
            String value = line.substring(eq + 1).strip();
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                    || value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(key, value);
        }
        return values;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}

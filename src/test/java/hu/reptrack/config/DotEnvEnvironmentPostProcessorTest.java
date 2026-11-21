package hu.reptrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** A .env fájl betöltése: UTF-8, környezeti változóként, a valódi környezeti változók elsőbbségével. */
class DotEnvEnvironmentPostProcessorTest {

    @TempDir Path dir;

    /** Spring-környezet a megadott "valódi" környezeti változókkal (a gépé helyett, hogy a teszt determinisztikus legyen). */
    private static StandardEnvironment environment(Map<String, Object> realVariables) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        realVariables));
        return env;
    }

    private StandardEnvironment load(String dotEnv, Map<String, Object> realVariables) throws IOException {
        Path file = dir.resolve(".env");
        Files.writeString(file, dotEnv, StandardCharsets.UTF_8);
        StandardEnvironment env = environment(realVariables);
        new DotEnvEnvironmentPostProcessor(file).postProcessEnvironment(env, null);
        return env;
    }

    @Test
    void readsUtf8ValuesWithCommentsQuotesAndExport() throws IOException {
        StandardEnvironment env = load("""
                # megjegyzés
                REPTRACK_ADMIN_NAME=Kónya Gergely
                REPTRACK_ADMIN_PASSWORD="Jelszó#2026 = biztos"
                export REPTRACK_ADMIN_EMAIL = 'admin@reptrack.hu'

                ez nem érvényes sor
                """, Map.of());

        assertThat(env.getProperty("REPTRACK_ADMIN_NAME")).isEqualTo("Kónya Gergely");
        assertThat(env.getProperty("REPTRACK_ADMIN_PASSWORD")).isEqualTo("Jelszó#2026 = biztos");
        assertThat(env.getProperty("REPTRACK_ADMIN_EMAIL")).isEqualTo("admin@reptrack.hu");
        assertThat(env.resolvePlaceholders("${REPTRACK_ADMIN_NAME:}")).isEqualTo("Kónya Gergely");
    }

    @Test
    void entriesBindLikeEnvironmentVariables() throws IOException {
        StandardEnvironment env = load("SERVER_PORT=8082\n", Map.of());

        assertThat(env.getProperty("server.port")).isEqualTo("8082");
        assertThat(Binder.get(env).bind("server.port", Integer.class).get()).isEqualTo(8082);
    }

    @Test
    void realEnvironmentVariablesWin() throws IOException {
        StandardEnvironment env = load("REPTRACK_ADMIN_EMAIL=fajlbol@reptrack.hu\nREPTRACK_ADMIN_NAME=Fájlból\n",
                Map.of("REPTRACK_ADMIN_EMAIL", "valodi@reptrack.hu"));

        assertThat(env.getProperty("REPTRACK_ADMIN_EMAIL")).isEqualTo("valodi@reptrack.hu");
        assertThat(env.getProperty("REPTRACK_ADMIN_NAME")).isEqualTo("Fájlból");
    }

    @Test
    void missingFileChangesNothing() {
        StandardEnvironment env = environment(Map.of());
        int before = env.getPropertySources().size();

        new DotEnvEnvironmentPostProcessor(dir.resolve("nincs-ilyen.env")).postProcessEnvironment(env, null);

        assertThat(env.getPropertySources().size()).isEqualTo(before);
        assertThat(env.getPropertySources().contains(DotEnvEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isFalse();
    }
}

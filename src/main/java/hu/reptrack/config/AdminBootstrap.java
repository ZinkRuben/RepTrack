package hu.reptrack.config;

import hu.reptrack.dto.DuplicateEmailException;
import hu.reptrack.dto.RegistrationRequest;
import hu.reptrack.service.UserService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Az első adminisztrátori fiók létrehozása induláskor (a Flyway-migrációk után), ha még nincs adminisztrátor.
 *
 * <p>Az adatok környezeti változókból jönnek (helyben a .env fájlból is, ami nem kerül a repóba):
 * {@code REPTRACK_ADMIN_EMAIL}, {@code REPTRACK_ADMIN_PASSWORD} és opcionálisan {@code REPTRACK_ADMIN_NAME}.
 *
 * <ul>
 *   <li>Van már adminisztrátor: nem történik semmi (a változók módosítása a meglévő jelszót sem írja át).</li>
 *   <li>Nincs adminisztrátor és nincs megadva adat: figyelmeztetés a naplóban, az alkalmazás elindul.</li>
 *   <li>Hibás adat, vagy a cím egy meglévő fiókhoz tartozik: az alkalmazás nem indul el, és a hibaüzenet megnevezi
 *       a javítandó változót. Meglévő fiókot nem léptet elő.</li>
 * </ul>
 *
 * A jelszó soha nem kerül a naplóba.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserService userService;
    private final String email;
    private final String password;
    private final String name;

    public AdminBootstrap(UserService userService,
                          @Value("${reptrack.admin.email:}") String email,
                          @Value("${reptrack.admin.password:}") String password,
                          @Value("${reptrack.admin.name:Adminisztrátor}") String name) {
        this.userService = userService;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userService.adminExists()) {
            log.debug("Van már adminisztrátori fiók, az első admin létrehozása kimarad.");
            return;
        }
        if (isBlank(email) && isBlank(password)) {
            log.warn("Nincs adminisztrátori fiók. Létrehozásához add meg a REPTRACK_ADMIN_EMAIL és a "
                    + "REPTRACK_ADMIN_PASSWORD környezeti változót (helyben a .env fájlban, lásd .env.example), "
                    + "majd indítsd újra az alkalmazást.");
            return;
        }
        if (isBlank(email) || isBlank(password)) {
            throw new IllegalStateException("Az első adminisztrátorhoz a REPTRACK_ADMIN_EMAIL és a "
                    + "REPTRACK_ADMIN_PASSWORD is kötelező; most csak az egyik van megadva.");
        }

        try {
            userService.createInitialAdmin(new RegistrationRequest(name, email, password, password));
        } catch (ConstraintViolationException e) {
            throw new IllegalStateException("Az első adminisztrátor adatai hibásak: " + describe(e), e);
        } catch (DuplicateEmailException e) {
            throw new IllegalStateException("A REPTRACK_ADMIN_EMAIL (" + email.strip() + ") már egy meglévő, "
                    + "nem adminisztrátor fiókhoz tartozik. Meglévő fiókot nem léptetünk elő: adj meg másik címet.", e);
        }
        log.info("Adminisztrátori fiók létrehozva: {}", email.strip().toLowerCase(Locale.ROOT));
    }

    /** A megsértett szabályok, a javítandó környezeti változó nevével. */
    private static String describe(ConstraintViolationException e) {
        return e.getConstraintViolations().stream()
                .map(v -> variableFor(v) + ": " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
    }

    private static String variableFor(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String property = path.substring(path.lastIndexOf('.') + 1);
        return switch (property) {
            case "email" -> "REPTRACK_ADMIN_EMAIL";
            case "displayName" -> "REPTRACK_ADMIN_NAME";
            default -> "REPTRACK_ADMIN_PASSWORD";
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

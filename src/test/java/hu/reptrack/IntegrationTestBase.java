package hu.reptrack;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Közös ős az integrációs teszteknek: a tesztek valódi PostgreSQL-lel, a Flyway migrációkkal futnak.
 * A konténer egyszer indul el az összes tesztosztályra (a Spring újrahasznosítja a kontextust,
 * ezért nem szabad osztályonként leállítani).
 *
 * <p>Az első admin adatai üresek: a fejlesztő saját .env fájlja így nem hoz létre adminisztrátort a tesztadatbázisban
 * (az AdminBootstrap tesztjei a futtatót közvetlenül hívják, tranzakcióban). A demófelhasználó ugyanezért ki van
 * kapcsolva (a DemoDataSeeder tesztjei szintén közvetlenül hívják). Ha egy tesztosztály saját {@code @SpringBootTest}
 * annotációt ad meg, az felülírja ezt a listát: ott is meg kell adni mindhárom beállítást.
 */
@SpringBootTest(properties = {"reptrack.admin.email=", "reptrack.admin.password=", "reptrack.demo.enabled=false"})
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    static {
        postgres.start();
    }
}

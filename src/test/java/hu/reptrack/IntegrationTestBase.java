package hu.reptrack;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Közös ős az integrációs teszteknek: a tesztek valódi PostgreSQL-lel, a Flyway migrációkkal futnak.
 * A konténer egyszer indul el az összes tesztosztályra (a Spring újrahasznosítja a kontextust,
 * ezért nem szabad osztályonként leállítani).
 */
@SpringBootTest
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    static {
        postgres.start();
    }
}

package hu.reptrack.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import hu.reptrack.IntegrationTestBase;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A V6 migráció a már meglévő állóképességi célokat is átalakítja: távcél lesz belőlük, ha a gyakorlat távot
 * rögzít, különben időcél; gyakorlat nélkül távcél. A célérték nem változik (a régi „Futás, 5” cél 5 km lesz).
 *
 * <p>A teszt egy külön sémában a V5-ig felépíti az adatbázist, felvesz régi formájú célokat, lefuttatja a V6-ot,
 * majd törli a sémát; a többi teszt adatait nem érinti.
 */
class EnduranceGoalMigrationTest extends IntegrationTestBase {

    private static final String SCHEMA = "v6_migracio_proba";

    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;

    private void migrateTo(String version) {
        HikariDataSource pool = (HikariDataSource) dataSource;
        Flyway.configure()
                .dataSource(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword())
                .schemas(SCHEMA)
                .target(version)
                .load()
                .migrate();
    }

    private Long exercise(String name) {
        return jdbc.queryForObject("select id from " + SCHEMA + ".exercises where name = ? and owner_id is null",
                Long.class, name);
    }

    @Test
    void existingEnduranceGoalsGetAMetricFromTheirExercise() {
        try {
            migrateTo("5");
            Long user = jdbc.queryForObject("insert into " + SCHEMA + ".users (email, password_hash, display_name) "
                    + "values ('migracio@example.com', 'h', 'M') returning id", Long.class);
            String insert = "insert into " + SCHEMA + ".goals (user_id, goal_type, exercise_id, target_value) values (?, ?, ?, ?)";
            jdbc.update(insert, user, "ENDURANCE", exercise("Futás"), 5);           // a „cél: 5 kg”-ként mutatott régi cél
            jdbc.update(insert, user, "ENDURANCE", exercise("Alkartámasz"), 3);     // csak időt rögzítő gyakorlat
            jdbc.update(insert, user, "ENDURANCE", null, null);                     // gyakorlat nélkül
            jdbc.update(insert, user, "STRENGTH", exercise("Guggolás"), 100);

            migrateTo("6");

            List<Map<String, Object>> rows = jdbc.queryForList("select goal_type, endurance_metric, target_distance_m, "
                    + "target_value from " + SCHEMA + ".goals order by id");
            assertThat(rows).extracting(r -> r.get("endurance_metric"))
                    .containsExactly("DISTANCE", "DURATION", "DISTANCE", null);
            assertThat(rows).extracting(r -> r.get("target_distance_m")).containsOnlyNulls();
            assertThat(new java.math.BigDecimal(rows.get(0).get("target_value").toString())).isEqualByComparingTo("5");
        } finally {
            jdbc.execute("drop schema if exists " + SCHEMA + " cascade");
        }
    }
}

package hu.reptrack.config;

import hu.reptrack.service.DemoDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * A demófelhasználó létrehozása induláskor (a Flyway-migrációk után), ha a {@code REPTRACK_DEMO_ENABLED} be van
 * kapcsolva; alapból ki van kapcsolva. Az adatokat a {@link DemoDataService} készíti.
 *
 * <ul>
 *   <li>Kikapcsolva: nem történik semmi.</li>
 *   <li>Bekapcsolva, és még nincs demófiók: létrejön 16 hétnyi mintaadattal.</li>
 *   <li>Bekapcsolva, és a fiók már létezik: nem változik (újra létrehozni a fiók törlése után lehet).</li>
 *   <li>Hiba esetén az alkalmazás demófiók nélkül indul el, a hiba a naplóba kerül: a demó nem része a
 *       működésnek, ezért nem akadályozhatja meg az indulást.</li>
 * </ul>
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final DemoDataService demoData;
    private final boolean enabled;

    public DemoDataSeeder(DemoDataService demoData, @Value("${reptrack.demo.enabled:false}") boolean enabled) {
        this.demoData = demoData;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            if (demoData.createIfMissing()) {
                log.info("Demófelhasználó létrehozva 16 hétnyi mintaadattal: {}", DemoDataService.EMAIL);
            } else {
                log.info("A demófelhasználó ({}) már létezik, az adatai nem változnak.", DemoDataService.EMAIL);
            }
        } catch (RuntimeException e) {
            log.error("A demófelhasználó létrehozása nem sikerült, az alkalmazás nélküle indul el.", e);
        }
    }
}

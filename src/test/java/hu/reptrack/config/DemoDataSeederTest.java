package hu.reptrack.config;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.service.DemoDataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * A demófelhasználó kapcsolója. A futtatót közvetlenül hívjuk, tranzakcióban, hogy a demófiók ne maradjon a többi
 * teszt által is használt adatbázisban. Az adatok tartalmát a DemoDataServiceTest ellenőrzi.
 */
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class DemoDataSeederTest extends IntegrationTestBase {

    @Autowired DemoDataService demoData;
    @Autowired UserRepository users;
    @Autowired DemoDataSeeder registeredRunner;

    private void runWith(boolean enabled) {
        new DemoDataSeeder(demoData, enabled).run(null);
    }

    private boolean demoExists() {
        return users.existsByEmailIgnoreCase(DemoDataService.EMAIL);
    }

    @Test
    void isRegisteredAsStartupRunnerAndTestsStartWithoutDemoAccount() {
        assertThat(registeredRunner).isNotNull();
        assertThat(demoExists()).isFalse();
    }

    @Test
    void switchedOffByDefaultNothingIsCreated() {
        runWith(false);

        assertThat(demoExists()).isFalse();
    }

    @Test
    void switchedOnCreatesTheDemoAccountOnceAndLogsIt(CapturedOutput output) {
        runWith(true);

        assertThat(demoExists()).isTrue();
        assertThat(output).contains("Demófelhasználó létrehozva", DemoDataService.EMAIL);
        assertThat(output).doesNotContain(DemoDataService.PASSWORD);

        runWith(true);

        assertThat(users.findAll()).filteredOn(u -> u.getEmail().equals(DemoDataService.EMAIL)).hasSize(1);
        assertThat(output).contains("már létezik");
    }
}

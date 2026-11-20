package hu.reptrack.repository;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.User;
import hu.reptrack.dto.FitnessLevel;
import hu.reptrack.dto.Role;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserRepositoryTest extends IntegrationTestBase {

    @Autowired
    UserRepository users;

    @Autowired
    EntityManager em;

    private User newUser(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setDisplayName("Teszt Elek");
        return u;
    }

    @Test
    void savesUserWithDefaults() {
        User saved = users.saveAndFlush(newUser("elek@example.com"));
        em.clear();

        User loaded = users.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getRole()).isEqualTo(Role.USER);
        assertThat(loaded.getFitnessLevel()).isEqualTo(FitnessLevel.BEGINNER);
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getLoadRatioThreshold()).isEqualByComparingTo("1.30");
        assertThat(loaded.getMuscleSetsThreshold()).isEqualTo((short) 6);
        assertThat(loaded.getFatigueThreshold()).isEqualTo((short) 8);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getVersion()).isNotNull();
    }

    @Test
    void emailLookupIsCaseInsensitive() {
        users.saveAndFlush(newUser("Elek@Example.com"));

        assertThat(users.findByEmailIgnoreCase("elek@example.COM")).isPresent();
        assertThat(users.existsByEmailIgnoreCase("ELEK@EXAMPLE.COM")).isTrue();
        assertThat(users.existsByEmailIgnoreCase("masik@example.com")).isFalse();
    }
}

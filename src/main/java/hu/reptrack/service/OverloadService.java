package hu.reptrack.service;

import hu.reptrack.domain.OverloadWarning;
import hu.reptrack.domain.User;
import hu.reptrack.domain.Workout;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.OverloadWarningView;
import hu.reptrack.repository.OverloadWarningRepository;
import hu.reptrack.repository.StatisticsRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.rules.OverloadDetector;
import hu.reptrack.rules.OverloadDetector.Finding;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A túlterhelési figyelmeztetések kezelése (UC12, UC16). A szabályokat az {@link OverloadDetector} értékeli ki a
 * felhasználó teljes előzményén; itt a tárolt figyelmeztetésekkel való egyeztetés történik:
 * <ul>
 *   <li>ami már nem érvényes (pl. az edzést szerkesztették vagy törölték), az törlődik;</li>
 *   <li>ami új, az létrejön nyugtázatlanul;</li>
 *   <li>ami már létezik, az megmarad, a nyugtázott állapotával együtt.</li>
 * </ul>
 * Csak az utolsó {@value #HORIZON_DAYS} nap eseményeit kezeli, így a régi előzményből nem jön létre figyelmeztetés-áradat.
 * Az egyeztetést az edzés mentése, módosítása és törlése után kell meghívni (a {@link WorkoutService} ezt megteszi).
 */
@Service
@Transactional
public class OverloadService {

    /** Ennél régebbi események figyelmeztetéseit nem hozzuk létre, és nem töröljük. */
    public static final int HORIZON_DAYS = 90;

    private final OverloadWarningRepository warnings;
    private final StatisticsRepository statistics;
    private final UserRepository users;
    private final Clock clock;
    private final ZoneId zone;

    @PersistenceContext
    private EntityManager em;

    public OverloadService(OverloadWarningRepository warnings, StatisticsRepository statistics, UserRepository users,
                           Clock clock, @Value("${reptrack.timezone:Europe/Budapest}") String timezone) {
        this.warnings = warnings;
        this.statistics = statistics;
        this.users = users;
        this.clock = clock;
        this.zone = ZoneId.of(timezone);
    }

    /**
     * Kiértékeli a szabályokat, és egyezteti a tárolt figyelmeztetéseket. Visszaadja az újonnan létrejötteket.
     */
    public List<OverloadWarningView> evaluate(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("A felhasználó nem található."));
        OverloadDetector.Thresholds thresholds = new OverloadDetector.Thresholds(
                user.getLoadRatioThreshold(), user.getMuscleSetsThreshold(), user.getFatigueThreshold());

        LocalDate cutoff = LocalDate.now(clock.withZone(zone)).minusDays(HORIZON_DAYS);
        List<Finding> findings = OverloadDetector.detect(
                statistics.findWorkouts(userId, null, null, null, null),
                statistics.findMuscleSetCounts(userId), thresholds, zone).stream()
                .filter(f -> !f.eventDate().isBefore(cutoff))
                .toList();

        Map<String, OverloadWarning> stored = new HashMap<>();
        for (OverloadWarning w : warnings.findByUserId(userId)) {
            stored.put(identity(w.getRuleCode().name(), w.getRuleKey()), w);
        }

        Set<String> current = new HashSet<>();
        List<OverloadWarning> touched = new ArrayList<>();
        List<OverloadWarning> created = new ArrayList<>();
        for (Finding f : findings) {
            String id = identity(f.rule().name(), f.key());
            current.add(id);
            OverloadWarning warning = stored.get(id);
            if (warning == null) {
                warning = new OverloadWarning();
                warning.setUser(user);
                warning.setRuleCode(f.rule());
                warning.setRuleKey(f.key());
                created.add(warning);
            }
            warning.setEventDate(f.eventDate());
            warning.setMessage(f.message());
            warning.setWorkout(f.workoutId() == null ? null : em.getReference(Workout.class, f.workoutId()));
            touched.add(warning);
        }
        for (Map.Entry<String, OverloadWarning> entry : stored.entrySet()) {
            OverloadWarning old = entry.getValue();
            if (!current.contains(entry.getKey()) && !old.getEventDate().isBefore(cutoff)) {
                warnings.delete(old);                      // a feltétel már nem áll fenn
            } else {
                touched.add(old);
            }
        }
        warnings.saveAll(touched);
        warnings.flush();
        // Nem tartjuk kezelt állapotban: egy későbbi törlés az edzést eltávolíthatja, amire a figyelmeztetés hivatkozik
        // (az adatbázis törli a sort), és a figyelmeztetések úgyis újraegyeztetődnek.
        touched.forEach(em::detach);
        Set<Long> createdIds = new HashSet<>();
        created.forEach(w -> createdIds.add(w.getId()));
        return warnings.findViewsByUser(userId).stream().filter(v -> createdIds.contains(v.id())).toList();
    }

    /** A felhasználó figyelmeztetései, a legfrissebb esemény elöl; alapértelmezetten csak a nyugtázatlanok. */
    @Transactional(readOnly = true)
    public List<OverloadWarningView> list(Long userId, boolean includeAcknowledged) {
        return includeAcknowledged ? warnings.findViewsByUser(userId) : warnings.findOpenViewsByUser(userId);
    }

    /** Az adott edzéshez tartozó figyelmeztetések (az edzés mentése utáni összefoglalóhoz). */
    @Transactional(readOnly = true)
    public List<OverloadWarningView> forWorkout(Long userId, Long workoutId) {
        return warnings.findViewsByWorkout(userId, workoutId);
    }

    /** Nyugtázás; más felhasználó figyelmeztetése nem található. */
    public void acknowledge(Long userId, Long warningId) {
        OverloadWarning warning = warnings.findByIdAndUserId(warningId, userId)
                .orElseThrow(() -> new NotFoundException("A figyelmeztetés nem található."));
        warning.setAcknowledged(true);
    }

    private static String identity(String rule, String key) {
        return rule + "|" + key;
    }
}

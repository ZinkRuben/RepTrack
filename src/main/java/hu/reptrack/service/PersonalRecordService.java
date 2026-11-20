package hu.reptrack.service;

import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.PersonalRecord;
import hu.reptrack.domain.User;
import hu.reptrack.domain.WorkoutSet;
import hu.reptrack.dto.PersonalRecordView;
import hu.reptrack.dto.RecordSet;
import hu.reptrack.repository.PersonalRecordRepository;
import hu.reptrack.repository.StatisticsRepository;
import hu.reptrack.rules.PersonalRecordDetector;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Az egyéni csúcsok (PR) kezelése (UC11, UC16). A PR-ek egy gyakorlat összes lezárt szettjéből, időrendi lejátszással
 * számolódnak ({@link PersonalRecordDetector}), ezért az újraszámolás minden módosítás után (mentés, szerkesztés,
 * törlés, korábbi dátumra rögzítés) ugyanazt az eredményt adja. A számítás az érintett gyakorlatok összes szettjét
 * használja, ezért a hívónak (a {@link WorkoutService}-nek) a módosítás után kell meghívnia.
 */
@Service
@Transactional
public class PersonalRecordService {

    private final PersonalRecordRepository records;
    private final StatisticsRepository statistics;
    private final ZoneId zone;

    @PersistenceContext
    private EntityManager em;

    public PersonalRecordService(PersonalRecordRepository records, StatisticsRepository statistics,
                                 @Value("${reptrack.timezone:Europe/Budapest}") String timezone) {
        this.records = records;
        this.statistics = statistics;
        this.zone = ZoneId.of(timezone);
    }

    /** Egy gyakorlat PR-jeinek újraszámolása és lecserélése. Visszaadja az új állapotot (a legújabb elöl). */
    public List<PersonalRecordView> recalculate(Long userId, Long exerciseId) {
        List<RecordSet> sets = statistics.findRecordSets(userId, exerciseId);
        List<PersonalRecordDetector.Found> found = PersonalRecordDetector.detect(sets, zone);

        records.deleteByUserAndExercise(userId, exerciseId);
        User user = em.getReference(User.class, userId);
        Exercise exercise = em.getReference(Exercise.class, exerciseId);
        List<PersonalRecord> saved = new ArrayList<>();
        for (PersonalRecordDetector.Found f : found) {
            PersonalRecord record = new PersonalRecord();
            record.setUser(user);
            record.setExercise(exercise);
            record.setWorkoutSet(em.getReference(WorkoutSet.class, f.setId()));
            record.setRecordType(f.type());
            record.setReps(f.reps());
            record.setDistanceBand(f.band());
            record.setValue(f.value());
            record.setAchievedOn(f.achievedOn());
            saved.add(records.save(record));
        }
        records.flush();
        // Nem tartjuk kezelt állapotban: egy későbbi szerkesztés vagy törlés a szettet eltávolíthatja, amire a PR
        // hivatkozik (az adatbázis törli a sort), a PR-ek pedig úgyis újraszámolódnak.
        saved.forEach(em::detach);
        return records.findViewsByUserAndExercise(userId, exerciseId);
    }

    /** Több gyakorlat újraszámolása (a duplikátumok kiszűrésével). */
    public void recalculate(Long userId, Collection<Long> exerciseIds) {
        for (Long exerciseId : new LinkedHashSet<>(exerciseIds)) {
            recalculate(userId, exerciseId);
        }
    }

    /** A felhasználó PR-jei időrendben, a legújabb elöl; gyakorlatra szűrhető. */
    @Transactional(readOnly = true)
    public List<PersonalRecordView> list(Long userId, Long exerciseId) {
        return exerciseId == null
                ? records.findViewsByUser(userId)
                : records.findViewsByUserAndExercise(userId, exerciseId);
    }

    /** Az adott edzésben elért PR-ek (az edzés mentése utáni összefoglalóhoz). */
    @Transactional(readOnly = true)
    public List<PersonalRecordView> achievedIn(Long userId, Long workoutId) {
        return records.findViewsByWorkout(userId, workoutId);
    }
}

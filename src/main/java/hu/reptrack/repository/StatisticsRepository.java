package hu.reptrack.repository;

import hu.reptrack.dto.BodyWeightPoint;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseOption;
import hu.reptrack.dto.MuscleSetCount;
import hu.reptrack.dto.RecordSet;
import hu.reptrack.dto.SetRecord;
import hu.reptrack.dto.WorkoutRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

/**
 * A statisztikához szükséges, entitások helyett csak a kellő oszlopokat visszaadó lekérdezések. Az opcionális szűrők
 * miatt a JPQL szövege állítódik össze (csak rögzített szövegrészekből, a felhasználói értékek paraméterek).
 */
@Repository
public class StatisticsRepository {

    @PersistenceContext
    private EntityManager em;

    /** A felhasználó edzései az időszakban; gyakorlat- vagy kategóriaszűrőnél csak a megfelelő szettet tartalmazók. */
    public List<WorkoutRow> findWorkouts(Long userId, Instant from, Instant to, Long exerciseId, ExerciseCategory category) {
        StringBuilder jpql = new StringBuilder(
                "select new hu.reptrack.dto.WorkoutRow(w.id, w.startedAt, w.endedAt, w.fatigue) "
                        + "from Workout w where w.user.id = :userId");
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        appendPeriod(jpql, params, from, to);
        if (exerciseId != null || category != null) {
            jpql.append(" and exists (select 1 from WorkoutExercise we where we.workout = w");
            if (exerciseId != null) {
                jpql.append(" and we.exercise.id = :exerciseId");
                params.put("exerciseId", exerciseId);
            }
            if (category != null) {
                jpql.append(" and we.exercise.category = :category");
                params.put("category", category);
            }
            jpql.append(")");
        }
        jpql.append(" order by w.startedAt, w.id");
        return run(jpql, params, WorkoutRow.class);
    }

    /** A felhasználó szettjei az időszakban, a szűrőknek megfelelően, időrendben. */
    public List<SetRecord> findSets(Long userId, Instant from, Instant to, Long exerciseId, ExerciseCategory category) {
        StringBuilder jpql = new StringBuilder(
                "select new hu.reptrack.dto.SetRecord(w.id, w.startedAt, w.endedAt, w.fatigue, e.id, e.name, e.category, "
                        + "e.cardioType, s.weightKg, s.reps, s.durationSec, s.distanceM) "
                        + "from WorkoutSet s join s.workoutExercise we join we.workout w join we.exercise e "
                        + "where w.user.id = :userId");
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        appendPeriod(jpql, params, from, to);
        if (exerciseId != null) {
            jpql.append(" and e.id = :exerciseId");
            params.put("exerciseId", exerciseId);
        }
        if (category != null) {
            jpql.append(" and e.category = :category");
            params.put("category", category);
        }
        jpql.append(" order by w.startedAt, w.id, we.position, s.position");
        return run(jpql, params, SetRecord.class);
    }

    /** Egy gyakorlat szettjei a felhasználó lezárt edzéseiből, időrendben (a PR-felismerés bemenete). */
    public List<RecordSet> findRecordSets(Long userId, Long exerciseId) {
        return em.createQuery("select new hu.reptrack.dto.RecordSet(s.id, w.id, w.startedAt, e.category, e.cardioType, "
                        + "s.weightKg, s.reps, s.durationSec, s.distanceM) "
                        + "from WorkoutSet s join s.workoutExercise we join we.workout w join we.exercise e "
                        + "where w.user.id = :userId and e.id = :exerciseId and w.endedAt is not null "
                        + "order by w.startedAt, w.id, s.id", RecordSet.class)
                .setParameter("userId", userId)
                .setParameter("exerciseId", exerciseId)
                .getResultList();
    }

    /** Edzésenként és elsődleges izomcsoportonként a szettek száma, a lezárt edzésekből (túlterhelési szabályokhoz). */
    public List<MuscleSetCount> findMuscleSetCounts(Long userId) {
        return em.createQuery("select new hu.reptrack.dto.MuscleSetCount(w.id, w.startedAt, e.primaryMuscle, count(s)) "
                        + "from WorkoutSet s join s.workoutExercise we join we.workout w join we.exercise e "
                        + "where w.user.id = :userId and w.endedAt is not null and e.primaryMuscle is not null "
                        + "group by w.id, w.startedAt, e.primaryMuscle", MuscleSetCount.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    /** Azok a gyakorlatok, amelyekből a felhasználónak van rögzített szettje (a fejlődési görbe választójához). */
    public List<ExerciseOption> findUsedExercises(Long userId) {
        return em.createQuery("select distinct new hu.reptrack.dto.ExerciseOption(e.id, e.name, e.category, e.tracksWeight, "
                        + "e.tracksReps, e.tracksDuration, e.tracksDistance, e.tracksHeartRate, "
                        + "case when e.owner is null then true else false end, e.primaryMuscle, e.imageKey) "
                        + "from WorkoutSet s join s.workoutExercise we join we.workout w join we.exercise e "
                        + "where w.user.id = :userId order by e.name", ExerciseOption.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    /** A felhasználó összes testtömeg-mérése időrendben (a számításokhoz az időszak előtti mérések is kellenek). */
    public List<BodyWeightPoint> findBodyWeights(Long userId) {
        return em.createQuery("select new hu.reptrack.dto.BodyWeightPoint(b.measuredOn, b.weightKg) "
                        + "from BodyWeightEntry b where b.user.id = :userId order by b.measuredOn", BodyWeightPoint.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    private void appendPeriod(StringBuilder jpql, Map<String, Object> params, Instant from, Instant to) {
        if (from != null) {
            jpql.append(" and w.startedAt >= :from");
            params.put("from", from);
        }
        if (to != null) {
            jpql.append(" and w.startedAt < :to");
            params.put("to", to);
        }
    }

    private <T> List<T> run(StringBuilder jpql, Map<String, Object> params, Class<T> type) {
        TypedQuery<T> query = em.createQuery(jpql.toString(), type);
        params.forEach(query::setParameter);
        return query.getResultList();
    }
}

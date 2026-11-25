package hu.reptrack.repository;

import hu.reptrack.domain.PersonalRecord;
import hu.reptrack.dto.PersonalRecordView;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, Long> {

    List<PersonalRecord> findByUserIdAndExerciseIdOrderByAchievedOnDesc(Long userId, Long exerciseId);

    /** Egy gyakorlat PR-jeinek törlése az újraszámolás előtt. */
    @Modifying(flushAutomatically = true)
    @Query("delete from PersonalRecord p where p.user.id = :userId and p.exercise.id = :exerciseId")
    int deleteByUserAndExercise(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    /** A felhasználó összes PR-je, a legújabb elöl. */
    @Query("""
            select new hu.reptrack.dto.PersonalRecordView(p.id, e.id, e.name, p.recordType, p.reps, p.distanceBand,
                   p.value, p.achievedOn, w.id)
            from PersonalRecord p join p.exercise e
                 left join p.workoutSet s left join s.workoutExercise we left join we.workout w
            where p.user.id = :userId
            order by p.achievedOn desc, p.id desc
            """)
    List<PersonalRecordView> findViewsByUser(@Param("userId") Long userId);

    /** Egy gyakorlat PR-jei, a legújabb elöl. */
    @Query("""
            select new hu.reptrack.dto.PersonalRecordView(p.id, e.id, e.name, p.recordType, p.reps, p.distanceBand,
                   p.value, p.achievedOn, w.id)
            from PersonalRecord p join p.exercise e
                 left join p.workoutSet s left join s.workoutExercise we left join we.workout w
            where p.user.id = :userId and e.id = :exerciseId
            order by p.achievedOn desc, p.id desc
            """)
    List<PersonalRecordView> findViewsByUserAndExercise(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    /** Az adott edzésben elért PR-ek. */
    @Query("""
            select new hu.reptrack.dto.PersonalRecordView(p.id, e.id, e.name, p.recordType, p.reps, p.distanceBand,
                   p.value, p.achievedOn, w.id)
            from PersonalRecord p join p.exercise e
                 join p.workoutSet s join s.workoutExercise we join we.workout w
            where p.user.id = :userId and w.id = :workoutId
            order by e.name, p.recordType, p.id
            """)
    List<PersonalRecordView> findViewsByWorkout(@Param("userId") Long userId, @Param("workoutId") Long workoutId);

    long countByUserId(Long userId);
}

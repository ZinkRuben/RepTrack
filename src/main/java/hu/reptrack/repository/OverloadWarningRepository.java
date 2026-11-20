package hu.reptrack.repository;

import hu.reptrack.domain.OverloadWarning;
import hu.reptrack.dto.OverloadWarningView;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OverloadWarningRepository extends JpaRepository<OverloadWarning, Long> {

    List<OverloadWarning> findByUserIdAndAcknowledgedFalseOrderByCreatedAtDesc(Long userId);

    List<OverloadWarning> findByUserId(Long userId);

    /** Tulajdonos-ellenőrzéssel: más felhasználó figyelmeztetése nem kérhető le. */
    Optional<OverloadWarning> findByIdAndUserId(Long id, Long userId);

    @Query("""
            select new hu.reptrack.dto.OverloadWarningView(w.id, w.ruleCode, w.message, w.eventDate, wo.id,
                   w.acknowledged, w.createdAt)
            from OverloadWarning w left join w.workout wo
            where w.user.id = :userId
            order by w.eventDate desc, w.id desc
            """)
    List<OverloadWarningView> findViewsByUser(@Param("userId") Long userId);

    @Query("""
            select new hu.reptrack.dto.OverloadWarningView(w.id, w.ruleCode, w.message, w.eventDate, wo.id,
                   w.acknowledged, w.createdAt)
            from OverloadWarning w left join w.workout wo
            where w.user.id = :userId and w.acknowledged = false
            order by w.eventDate desc, w.id desc
            """)
    List<OverloadWarningView> findOpenViewsByUser(@Param("userId") Long userId);

    @Query("""
            select new hu.reptrack.dto.OverloadWarningView(w.id, w.ruleCode, w.message, w.eventDate, wo.id,
                   w.acknowledged, w.createdAt)
            from OverloadWarning w join w.workout wo
            where w.user.id = :userId and wo.id = :workoutId
            order by w.id
            """)
    List<OverloadWarningView> findViewsByWorkout(@Param("userId") Long userId, @Param("workoutId") Long workoutId);
}

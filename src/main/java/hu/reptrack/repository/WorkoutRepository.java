package hu.reptrack.repository;

import hu.reptrack.domain.Workout;
import hu.reptrack.dto.WorkoutSummary;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkoutRepository extends JpaRepository<Workout, Long> {

    Page<Workout> findByUserId(Long userId, Pageable pageable);

    /** Tulajdonos-ellenőrzéssel: más felhasználó edzése nem kérhető le. */
    Optional<Workout> findByIdAndUserId(Long id, Long userId);

    /** Listanézet: egy lekérdezéssel, a gyakorlatok darabszámával (nem tölti be a teljes gráfot). */
    @Query(value = """
            select new hu.reptrack.dto.WorkoutSummary(w.id, w.title, w.startedAt, w.endedAt, w.fatigue, size(w.exercises))
            from Workout w
            where w.user.id = :userId
            """,
            countQuery = "select count(w) from Workout w where w.user.id = :userId")
    Page<WorkoutSummary> findSummariesByUserId(@Param("userId") Long userId, Pageable pageable);
}

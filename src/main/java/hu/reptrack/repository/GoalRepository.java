package hu.reptrack.repository;

import hu.reptrack.domain.Goal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserIdAndActiveTrue(Long userId);

    List<Goal> findByUserIdAndActiveTrueOrderByCreatedAtDesc(Long userId);

    /** Tulajdonos-ellenőrzéssel: más felhasználó célja nem kérhető le. */
    Optional<Goal> findByIdAndUserId(Long id, Long userId);
}

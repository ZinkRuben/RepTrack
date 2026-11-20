package hu.reptrack.repository;

import hu.reptrack.domain.Goal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserIdAndActiveTrue(Long userId);
}

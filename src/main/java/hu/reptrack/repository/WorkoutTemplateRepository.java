package hu.reptrack.repository;

import hu.reptrack.domain.WorkoutTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutTemplateRepository extends JpaRepository<WorkoutTemplate, Long> {

    List<WorkoutTemplate> findByUserIdOrderByName(Long userId);

    long countByUserId(Long userId);
}

package hu.reptrack.repository;

import hu.reptrack.domain.Workout;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutRepository extends JpaRepository<Workout, Long>, WorkoutRepositoryCustom {

    Page<Workout> findByUserId(Long userId, Pageable pageable);

    /** Tulajdonos-ellenőrzéssel: más felhasználó edzése nem kérhető le. */
    Optional<Workout> findByIdAndUserId(Long id, Long userId);
}

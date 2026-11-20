package hu.reptrack.repository;

import hu.reptrack.dto.WorkoutFilter;
import hu.reptrack.dto.WorkoutSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Dinamikusan szűrhető edzéslista (a feltételek elhagyhatók, ezért Criteria API-val épül). */
public interface WorkoutRepositoryCustom {

    Page<WorkoutSummary> search(Long userId, WorkoutFilter filter, Pageable pageable);
}

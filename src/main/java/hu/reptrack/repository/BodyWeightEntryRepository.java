package hu.reptrack.repository;

import hu.reptrack.domain.BodyWeightEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BodyWeightEntryRepository extends JpaRepository<BodyWeightEntry, Long> {
}

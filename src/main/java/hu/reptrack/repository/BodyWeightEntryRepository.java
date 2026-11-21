package hu.reptrack.repository;

import hu.reptrack.domain.BodyWeightEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BodyWeightEntryRepository extends JpaRepository<BodyWeightEntry, Long> {

    List<BodyWeightEntry> findByUserIdOrderByMeasuredOnDesc(Long userId);

    Optional<BodyWeightEntry> findByUserIdAndMeasuredOn(Long userId, LocalDate measuredOn);

    /** Tulajdonos-ellenőrzéssel: más felhasználó mérése nem kérhető le. */
    Optional<BodyWeightEntry> findByIdAndUserId(Long id, Long userId);
}

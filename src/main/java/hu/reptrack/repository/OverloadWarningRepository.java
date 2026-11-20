package hu.reptrack.repository;

import hu.reptrack.domain.OverloadWarning;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverloadWarningRepository extends JpaRepository<OverloadWarning, Long> {

    List<OverloadWarning> findByUserIdAndAcknowledgedFalseOrderByCreatedAtDesc(Long userId);
}

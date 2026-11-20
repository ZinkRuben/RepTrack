package hu.reptrack.repository;

import hu.reptrack.domain.PersonalRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalRecordRepository extends JpaRepository<PersonalRecord, Long> {

    List<PersonalRecord> findByUserIdAndExerciseIdOrderByAchievedOnDesc(Long userId, Long exerciseId);
}

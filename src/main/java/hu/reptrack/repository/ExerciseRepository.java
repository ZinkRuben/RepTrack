package hu.reptrack.repository;

import hu.reptrack.domain.Exercise;
import hu.reptrack.dto.MuscleGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /** Pontos egyezés a másodlagos izomcsoportra (nem szövegkeresés, így a BACK nem találja meg a LOWER_BACK-et). */
    @Query("select distinct e from Exercise e join e.secondaryMuscles m where m = :muscle")
    List<Exercise> findBySecondaryMuscle(@Param("muscle") MuscleGroup muscle);
}

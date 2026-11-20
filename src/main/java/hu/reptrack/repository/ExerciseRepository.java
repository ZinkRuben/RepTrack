package hu.reptrack.repository;

import hu.reptrack.domain.Exercise;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.MuscleGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /** Pontos egyezés a másodlagos izomcsoportra (nem szövegkeresés, így a BACK nem találja meg a LOWER_BACK-et). */
    @Query("select distinct e from Exercise e join e.secondaryMuscles m where m = :muscle")
    List<Exercise> findBySecondaryMuscle(@Param("muscle") MuscleGroup muscle);

    /**
     * A közös, nem archivált gyakorlatok név szerint rendezve; bármelyik szűrő lehet null. A {@code namePattern}
     * kisbetűs LIKE-minta ({@code \} az escape karakter). Az izomcsoport az elsődleges és a másodlagos izmokra is
     * illeszkedik. A másodlagos izmok ugyanebben a lekérdezésben töltődnek be (nincs gyakorlatonkénti külön lekérdezés).
     */
    @Query("""
            select e from Exercise e
            left join fetch e.secondaryMuscles
            where e.owner is null and e.archived = false
              and (:category is null or e.category = :category)
              and (:muscle is null or e.primaryMuscle = :muscle or :muscle member of e.secondaryMuscles)
              and (:namePattern is null or lower(e.name) like :namePattern escape '\\')
            order by e.name
            """)
    List<Exercise> searchShared(@Param("namePattern") String namePattern,
                                @Param("category") ExerciseCategory category,
                                @Param("muscle") MuscleGroup muscle);
}

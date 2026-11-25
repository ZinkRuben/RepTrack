package hu.reptrack.repository;

import hu.reptrack.domain.Exercise;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.MuscleGroup;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /** Pontos egyezés a másodlagos izomcsoportra (nem szövegkeresés, így a BACK nem találja meg a LOWER_BACK-et). */
    @Query("select distinct e from Exercise e join e.secondaryMuscles m where m = :muscle")
    List<Exercise> findBySecondaryMuscle(@Param("muscle") MuscleGroup muscle);

    /**
     * A felhasználó által látható gyakorlatok (a közösek és a saját privátjai, másokéi soha) név szerint rendezve;
     * a szűrők null értéke nem szűr. A {@code namePattern} kisbetűs LIKE-minta ({@code \} az escape karakter), az
     * izomcsoport az elsődleges és a másodlagos izmokra is illeszkedik.
     *
     * <p>{@code archived}: az archivált vagy az aktív gyakorlatok. Archivált közös gyakorlat csak akkor jelenik meg, ha
     * {@code sharedArchivedVisible} (adminisztrátor). A másodlagos izmok ugyanebben a lekérdezésben töltődnek be.
     */
    @Query("""
            select e from Exercise e
            left join fetch e.secondaryMuscles
            where (e.owner is null or e.owner.id = :userId)
              and e.archived = :archived
              and (:archived = false or e.owner is not null or :sharedArchivedVisible = true)
              and (:onlyShared = false or e.owner is null)
              and (:onlyOwn = false or e.owner is not null)
              and (:category is null or e.category = :category)
              and (:muscle is null or e.primaryMuscle = :muscle or :muscle member of e.secondaryMuscles)
              and (:namePattern is null or lower(e.name) like :namePattern escape '\\')
            order by e.name
            """)
    List<Exercise> searchVisible(@Param("userId") Long userId,
                                 @Param("namePattern") String namePattern,
                                 @Param("category") ExerciseCategory category,
                                 @Param("muscle") MuscleGroup muscle,
                                 @Param("onlyShared") boolean onlyShared,
                                 @Param("onlyOwn") boolean onlyOwn,
                                 @Param("archived") boolean archived,
                                 @Param("sharedArchivedVisible") boolean sharedArchivedVisible);

    // ---- egyedi nevek (a séma egyedi indexeihez igazodva: közösek között, illetve felhasználónként) ----

    boolean existsByOwnerIsNullAndNameIgnoreCase(String name);

    boolean existsByOwnerIsNullAndNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);

    boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(Long ownerId, String name, Long id);

    // ---- használatban lévő gyakorlatok (ezek nem törölhetők, csak archiválhatók) ----

    @Query("select distinct we.exercise.id from WorkoutExercise we where we.exercise.id in :ids")
    Set<Long> findIdsUsedInWorkouts(@Param("ids") Collection<Long> ids);

    @Query("select distinct te.exercise.id from TemplateExercise te where te.exercise.id in :ids")
    Set<Long> findIdsUsedInTemplates(@Param("ids") Collection<Long> ids);

    @Query("select distinct g.exercise.id from Goal g where g.exercise.id in :ids")
    Set<Long> findIdsUsedInGoals(@Param("ids") Collection<Long> ids);

    long countByOwnerId(Long ownerId);
}

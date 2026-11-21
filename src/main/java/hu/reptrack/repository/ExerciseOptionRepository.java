package hu.reptrack.repository;

import hu.reptrack.domain.Exercise;
import hu.reptrack.dto.ExerciseOption;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Az edzés űrlapjához választható gyakorlatok (külön repository, hogy ne ütközzön a gyakorlat-böngésző kódjával). */
public interface ExerciseOptionRepository extends Repository<Exercise, Long> {

    /** A felhasználó számára elérhető, nem archivált gyakorlatok: a közösek és a saját privátjai, név szerint. */
    @Query("""
            select new hu.reptrack.dto.ExerciseOption(e.id, e.name, e.category, e.tracksWeight, e.tracksReps,
                   e.tracksDuration, e.tracksDistance, e.tracksHeartRate,
                   case when e.owner is null then true else false end)
            from Exercise e
            where e.archived = false and (e.owner is null or e.owner.id = :userId)
            order by e.name
            """)
    List<ExerciseOption> findAvailable(@Param("userId") Long userId);
}

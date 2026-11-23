package hu.reptrack.repository;

import hu.reptrack.domain.WorkoutTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Edzéssablonok (UC15). A sablon gyakorlatai a sablonnal együtt töltődnek be ({@link EntityGraph}), így a lista
 * nem kérdez le sablononként külön.
 */
public interface WorkoutTemplateRepository extends JpaRepository<WorkoutTemplate, Long> {

    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    List<WorkoutTemplate> findByUserIdOrderByName(Long userId);

    /** Tulajdonos-ellenőrzéssel: más felhasználó sablona nem kérhető le. */
    @EntityGraph(attributePaths = {"exercises", "exercises.exercise"})
    Optional<WorkoutTemplate> findByIdAndUserId(Long id, Long userId);

    /** Van-e már ilyen nevű sablonja a felhasználónak (kis- és nagybetűtől függetlenül). */
    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    /** Mint az előző, de a módosított sablont magát nem számítja. */
    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(Long userId, String name, Long id);
}

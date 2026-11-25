package hu.reptrack.repository;

import hu.reptrack.domain.User;
import hu.reptrack.dto.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    /** Fióklista az adminisztrátornak (UC13), keresés e-mail-címre vagy névre (kis- és nagybetűtől függetlenül). */
    Page<User> findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(String email, String displayName,
                                                                                 Pageable pageable);

    /**
     * Az adott szerepkörű fiókok sorzárral (SELECT ... FOR UPDATE). Az „utolsó aktív adminisztrátor” ellenőrzése
     * ezzel működik egyidejű kérések mellett is: két adminisztrátor nem tudja egyszerre egymást letiltani.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role order by u.id")
    List<User> lockByRole(@Param("role") Role role);
}

package hu.reptrack.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

/**
 * Fiók törlése minden adatával, meghatározott sorrendben.
 *
 * <p>A felhasználó tábláinak idegen kulcsai {@code ON DELETE CASCADE}-esek, de a gyakorlatokra mutató hivatkozások
 * ({@code workout_exercises}, {@code template_exercises}, {@code goals}, {@code personal_records} →
 * {@code exercises}) szándékosan nem: így a használatban lévő gyakorlat nem törölhető. Ha a felhasználó saját
 * gyakorlatára edzés is hivatkozik, a {@code users} sor puszta törlésekor a PostgreSQL ezért a gyakorlat
 * kaszkádolt törlését elutasítja. A hivatkozó sorok ezért előbb törlődnek, a fiók (a saját gyakorlataival,
 * testtömeg-naplójával) utoljára. Ha egy másik fiók adata hivatkozna a felhasználó saját gyakorlatára (ezt a
 * szolgáltatások nem engedik), a törlés hibával megáll, és semmi sem törlődik.
 */
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    /** Sorrendben: ami gyakorlatra hivatkozik, előbb (a további sorokat az adatbázis kaszkádja viszi). */
    private static final List<String> STATEMENTS = List.of(
            "DELETE FROM personal_records WHERE user_id = ?1",
            "DELETE FROM overload_warnings WHERE user_id = ?1",
            "DELETE FROM workouts WHERE user_id = ?1",            // → workout_exercises → workout_sets
            "DELETE FROM workout_templates WHERE user_id = ?1",   // → template_exercises
            "DELETE FROM goals WHERE user_id = ?1",
            "DELETE FROM users WHERE id = ?1");                   // → exercises, body_weight_entries

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean deleteAccountWithAllData(Long userId) {
        em.flush();
        int deletedUsers = 0;
        for (String sql : STATEMENTS) {
            deletedUsers = em.createNativeQuery(sql).setParameter(1, userId).executeUpdate();
        }
        // a memóriában maradt (most már törölt) entitások ne kerüljenek vissza
        em.clear();
        return deletedUsers == 1;
    }
}

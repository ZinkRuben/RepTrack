package hu.reptrack.service;

import hu.reptrack.domain.User;
import hu.reptrack.dto.Role;
import hu.reptrack.repository.UserRepository;

/**
 * Az „utolsó aktív adminisztrátor” védelme: a rendszer nem maradhat működő adminisztrátor nélkül, mert akkor senki
 * nem kezelhetné a fiókokat és a közös gyakorlatadatbázist (az AdminBootstrap csak a következő induláskor hozna
 * létre újat). A szerepkör sorait zárolja, így két egyidejű kérés sem tudja kijátszani.
 */
final class AdminSafeguard {

    private AdminSafeguard() {
    }

    /**
     * Igaz, ha a {@code target} fiók elvesztése (letiltás, visszaminősítés, törlés) után nem maradna aktív
     * adminisztrátor. Tranzakción belül kell hívni (sorzár).
     */
    static boolean isLastActiveAdmin(UserRepository users, User target) {
        if (target.getRole() != Role.ADMIN || !target.isEnabled()) {
            return false;
        }
        return users.lockByRole(Role.ADMIN).stream()
                .filter(User::isEnabled)
                .noneMatch(u -> !u.getId().equals(target.getId()));
    }
}

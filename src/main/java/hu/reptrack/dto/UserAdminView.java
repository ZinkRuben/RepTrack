package hu.reptrack.dto;

import java.time.Instant;

/**
 * Egy fiók az adminisztrátori fióklistában (UC13). Szándékosan szűk: egészségügyi jellegű adatot (testtömeg,
 * pulzus) és edzésadatot nem tartalmaz, mert azt az adminisztrátor sem láthatja (specifikáció, adatvédelem).
 */
public record UserAdminView(
        Long id,
        String email,
        String displayName,
        Role role,
        boolean enabled,
        boolean passwordChangeRequired,
        Instant createdAt,
        Long version) {
}

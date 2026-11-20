package hu.reptrack.dto;

import java.time.Instant;

/** Egy edzés alapadatai az összesítőkhöz (a szettektől függetlenül, így az üres edzés is megszámolható). */
public record WorkoutRow(Long id, Instant startedAt, Instant endedAt, Short fatigue) {
}

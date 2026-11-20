package hu.reptrack.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Egy túlterhelési figyelmeztetés; az {@code eventDate} az az nap, amikor a szabály teljesült. */
public record OverloadWarningView(
        Long id,
        OverloadRule rule,
        String message,
        LocalDate eventDate,
        Long workoutId,
        boolean acknowledged,
        Instant createdAt) {
}

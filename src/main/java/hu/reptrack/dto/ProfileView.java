package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A felhasználó profilja. A testtömeg nincs a profilban tárolva: a legutóbbi mérés a testtömeg-naplóból jön
 * ({@code latestWeightKg}), a testtömegindex ebből és a magasságból számolódik (nincs adat esetén null).
 */
public record ProfileView(
        String email,
        String displayName,
        LocalDate birthDate,
        Short heightCm,
        FitnessLevel fitnessLevel,
        BigDecimal loadRatioThreshold,
        Short muscleSetsThreshold,
        Short fatigueThreshold,
        Long version,
        BigDecimal latestWeightKg,
        LocalDate latestWeightDate,
        BigDecimal bmi) {
}

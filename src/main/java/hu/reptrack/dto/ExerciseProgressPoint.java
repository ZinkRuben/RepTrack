package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Egy gyakorlat eredménye egy edzésen (a fejlődési görbe egy pontja). Ami nem értelmezhető az adott gyakorlatra,
 * az null: a tempó csak futásnál, a sebesség csak kerékpárnál, az 1RM és a relatív erő csak súlyos szettnél.
 */
public record ExerciseProgressPoint(
        LocalDate date,
        Long workoutId,
        BigDecimal bestWeightKg,
        BigDecimal estimated1Rm,
        BigDecimal volumeKg,
        BigDecimal paceMinPerKm,
        BigDecimal speedKmh,
        BigDecimal relativeStrength) {
}

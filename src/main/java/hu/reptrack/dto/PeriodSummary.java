package hu.reptrack.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Egy hét vagy hónap összesítője.
 *
 * @param periodStart     a időszak első napja (a hét hétfője, illetve a hónap 1. napja)
 * @param workouts        az időszakban kezdett edzések száma
 * @param totalMinutes    a lezárt edzések összideje percben
 * @param totalVolumeKg   az összvolumen (súly × ismétlés, saját testsúlyosnál a testtömeg is számít)
 * @param distanceKm      a szettekben rögzített távolságok összege
 * @param trainingLoad    a lezárt edzések terhelése (fáradtság × perc) összesen
 * @param setsByCategory  a szettek száma kategóriánként (mindhárom kategória szerepel, 0 is)
 */
public record PeriodSummary(
        LocalDate periodStart,
        int workouts,
        BigDecimal totalMinutes,
        BigDecimal totalVolumeKg,
        BigDecimal distanceKm,
        BigDecimal trainingLoad,
        Map<ExerciseCategory, Integer> setsByCategory) {
}

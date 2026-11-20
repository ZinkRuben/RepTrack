package hu.reptrack.rules;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.dto.Granularity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RulesBasicsTest {

    // ---- 1RM (Epley) ----

    @Test
    void estimatesOneRepMaxWithEpley() {
        assertThat(OneRepMax.estimate(new BigDecimal("100"), 5)).isEqualByComparingTo("116.7");
        assertThat(OneRepMax.estimate(new BigDecimal("80"), 10)).isEqualByComparingTo("106.7");
        assertThat(OneRepMax.estimate(new BigDecimal("100"), 1)).isEqualByComparingTo("103.3");
    }

    @Test
    void oneRepMaxIsReliableUpToTenReps() {
        assertThat(OneRepMax.isReliable((short) 1)).isTrue();
        assertThat(OneRepMax.isReliable((short) 10)).isTrue();
        assertThat(OneRepMax.isReliable((short) 11)).isFalse();
        assertThat(OneRepMax.isReliable((short) 0)).isFalse();
        assertThat(OneRepMax.isReliable(null)).isFalse();
    }

    // ---- tempó és sebesség ----

    @Test
    void computesPaceInMinutesPerKilometer() {
        assertThat(CardioMetrics.paceMinPerKm(1500, 5000)).isEqualByComparingTo("5.00");
        assertThat(CardioMetrics.paceMinPerKm(1650, 5000)).isEqualByComparingTo("5.50");
        assertThat(CardioMetrics.paceMinPerKm(1800, 10000)).isEqualByComparingTo("3.00");
    }

    @Test
    void computesSpeedInKilometersPerHour() {
        assertThat(CardioMetrics.speedKmh(3600, 30000)).isEqualByComparingTo("30.0");
        assertThat(CardioMetrics.speedKmh(1800, 15000)).isEqualByComparingTo("30.0");
        assertThat(CardioMetrics.speedKmh(1200, 10000)).isEqualByComparingTo("30.0");
    }

    @Test
    void paceAndSpeedAreNullWithoutDurationOrDistance() {
        assertThat(CardioMetrics.paceMinPerKm(0, 5000)).isNull();
        assertThat(CardioMetrics.paceMinPerKm(1500, 0)).isNull();
        assertThat(CardioMetrics.speedKmh(0, 5000)).isNull();
        assertThat(CardioMetrics.speedKmh(1500, 0)).isNull();
    }

    // ---- időszakok ----

    @Test
    void weekStartsOnMonday() {
        assertThat(Periods.start(LocalDate.of(2026, 11, 16), Granularity.WEEK)).isEqualTo(LocalDate.of(2026, 11, 16)); // hétfő
        assertThat(Periods.start(LocalDate.of(2026, 11, 21), Granularity.WEEK)).isEqualTo(LocalDate.of(2026, 11, 16)); // szombat
        assertThat(Periods.start(LocalDate.of(2026, 11, 22), Granularity.WEEK)).isEqualTo(LocalDate.of(2026, 11, 16)); // vasárnap
        assertThat(Periods.start(LocalDate.of(2026, 11, 23), Granularity.WEEK)).isEqualTo(LocalDate.of(2026, 11, 23)); // következő hétfő
    }

    @Test
    void monthStartsOnFirstDay() {
        assertThat(Periods.start(LocalDate.of(2026, 11, 21), Granularity.MONTH)).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(Periods.next(LocalDate.of(2026, 12, 1), Granularity.MONTH)).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(Periods.next(LocalDate.of(2026, 11, 16), Granularity.WEEK)).isEqualTo(LocalDate.of(2026, 11, 23));
    }

    // ---- edzésterhelés ----

    @Test
    void trainingLoadIsFatigueTimesMinutes() {
        Instant start = Instant.parse("2026-11-09T17:00:00Z");
        BigDecimal minutes = TrainingLoad.minutes(start, start.plusSeconds(50 * 60));
        assertThat(minutes).isEqualByComparingTo("50.0");
        assertThat(TrainingLoad.of((short) 7, minutes)).isEqualByComparingTo("350.0");

        BigDecimal odd = TrainingLoad.minutes(start, start.plusSeconds(45 * 60 + 30));
        assertThat(odd).isEqualByComparingTo("45.5");
        assertThat(TrainingLoad.of((short) 8, odd)).isEqualByComparingTo("364.0");
    }
}

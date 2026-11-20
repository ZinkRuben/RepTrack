package hu.reptrack.rules;

import hu.reptrack.dto.Granularity;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Időszakok kezdőnapjai: a hét hétfőn kezdődik, a hónap az 1. napon. */
public final class Periods {

    private Periods() {
    }

    public static LocalDate start(LocalDate date, Granularity granularity) {
        return switch (granularity) {
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    public static LocalDate next(LocalDate periodStart, Granularity granularity) {
        return switch (granularity) {
            case WEEK -> periodStart.plusWeeks(1);
            case MONTH -> periodStart.plusMonths(1);
        };
    }
}

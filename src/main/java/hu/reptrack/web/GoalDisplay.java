package hu.reptrack.web;

import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.EnduranceMetric;
import hu.reptrack.dto.GoalProgress;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.GoalView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Egy edzéscél a profil oldalán: a cél és a haladás szövege a céltípus egységével. Testtömeg- és erő-célnál kg,
 * állóképességi célnál a mérték szerint km, perc, vagy idő adott távon („5 km 25:00 alatt (5:00/km)”). Ha a haladás
 * nem számolható, a {@code missing} mondja meg, mi hiányzik hozzá.
 */
public record GoalDisplay(Long id, GoalType type, String exerciseName, String target, LocalDate deadline,
                          BigDecimal percent, String progress, String missing) {

    private static final Pattern MINUTES = Pattern.compile("(\\d{1,3})");
    private static final Pattern MINUTES_SECONDS = Pattern.compile("(\\d{1,3}):([0-5]\\d)");
    private static final Pattern HOURS_MINUTES_SECONDS = Pattern.compile("(\\d{1,2}):([0-5]\\d):([0-5]\\d)");

    static GoalDisplay of(GoalView goal, GoalProgress progress) {
        BigDecimal percent = progress == null ? null : progress.percent();
        return new GoalDisplay(goal.id(), goal.type(), goal.exerciseName(), target(goal), goal.deadline(), percent,
                percent == null ? null : progressText(goal, progress), percent == null ? missing(goal) : null);
    }

    private static boolean bodyWeight(GoalType type) {
        return type == GoalType.WEIGHT_LOSS || type == GoalType.MUSCLE_GAIN;
    }

    private static String target(GoalView goal) {
        BigDecimal value = goal.targetValue();
        if (goal.type() != GoalType.ENDURANCE) {
            return value == null ? null : "cél: " + number(value) + " kg";
        }
        return switch (goal.enduranceMetric()) {
            case DISTANCE -> value == null ? null : "cél: " + number(value) + " km egyben";
            case DURATION -> value == null ? null : "cél: " + number(value) + " perc egyben";
            case TIME_FOR_DISTANCE -> value == null ? "cél: " + km(goal.targetDistanceM())
                    : "cél: " + km(goal.targetDistanceM()) + " " + time(value) + " alatt ("
                    + pace(value, goal.targetDistanceM()) + "/km)";
        };
    }

    private static String progressText(GoalView goal, GoalProgress p) {
        String percent = number(p.percent()) + "%";
        if (p.current() == null) {
            return percent;
        }
        if (bodyWeight(goal.type())) {
            return percent + " · jelenleg " + number(p.current()) + " kg";
        }
        if (goal.type() == GoalType.STRENGTH) {
            return percent + " · eddigi legjobb: " + number(p.current()) + " kg";
        }
        return switch (goal.enduranceMetric()) {
            case DISTANCE -> percent + " · eddigi leghosszabb: " + number(p.current()) + " km";
            case DURATION -> percent + " · eddigi leghosszabb: " + number(p.current()) + " perc";
            case TIME_FOR_DISTANCE -> percent + " · eddigi legjobb: " + time(p.current()) + " ("
                    + pace(p.current(), goal.targetDistanceM()) + "/km)"
                    + (p.start() != null && p.start().compareTo(p.current()) != 0
                            ? " · kiindulás: " + time(p.start()) : "");
        };
    }

    private static String missing(GoalView goal) {
        if (bodyWeight(goal.type())) {
            return goal.targetValue() == null ? "A haladáshoz add meg a cél testtömeget."
                    : "A haladáshoz rögzíts legalább egy testtömeg-mérést.";
        }
        if (goal.exerciseId() == null) {
            return "A haladáshoz válassz gyakorlatot is.";
        }
        if (goal.targetValue() == null) {
            return "A haladáshoz célérték kell.";
        }
        if (goal.enduranceMetric() == EnduranceMetric.TIME_FOR_DISTANCE) {
            return "Még nincs legalább " + km(goal.targetDistanceM()) + " hosszú edzésed ezzel a gyakorlattal.";
        }
        return "Még nincs edzésed ezzel a gyakorlattal.";
    }

    // ---- formázás és beolvasás ----

    private static String number(BigDecimal value) {
        return FormValues.display(value);
    }

    private static String km(Integer meters) {
        return meters == null ? "?" : FormValues.display(FormValues.kilometers(meters)) + " km";
    }

    /** Percben megadott idő perc:másodperc (vagy óra:perc:másodperc) alakban, pl. 25:00, 3:30:00. */
    static String time(BigDecimal minutes) {
        long seconds = minutes.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValue();
        long hours = seconds / 3600;
        long mins = seconds % 3600 / 60;
        long secs = seconds % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, mins, secs) : String.format("%d:%02d", mins, secs);
    }

    /** A tempó (perc/km) az idő és a táv alapján, perc:másodperc alakban. */
    private static String pace(BigDecimal minutes, Integer meters) {
        if (meters == null || meters <= 0) {
            return "?";
        }
        return time(minutes.multiply(BigDecimal.valueOf(1000)).divide(BigDecimal.valueOf(meters), 4, RoundingMode.HALF_UP));
    }

    /**
     * A célidő beolvasása az űrlapról: perc:másodperc (25:00), óra:perc:másodperc (3:30:00) vagy csak perc (25).
     * Az eredmény percben (két tizedesre); üres szövegnél null.
     */
    static BigDecimal parseTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.strip();
        long seconds;
        Matcher m;
        if ((m = HOURS_MINUTES_SECONDS.matcher(value)).matches()) {
            seconds = Long.parseLong(m.group(1)) * 3600 + Long.parseLong(m.group(2)) * 60 + Long.parseLong(m.group(3));
        } else if ((m = MINUTES_SECONDS.matcher(value)).matches()) {
            seconds = Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2));
        } else if ((m = MINUTES.matcher(value)).matches()) {
            seconds = Long.parseLong(m.group(1)) * 60;
        } else {
            throw new BusinessRuleException("A célidőt perc:másodperc alakban add meg, például 25:00 "
                    + "(hosszabb távnál óra:perc:másodperc, például 3:30:00).");
        }
        return BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}

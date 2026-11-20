package hu.reptrack.rules;

import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.MuscleSetCount;
import hu.reptrack.dto.OverloadRule;
import hu.reptrack.dto.Granularity;
import hu.reptrack.dto.WorkoutRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A túlterhelési szabályok kiértékelése a felhasználó teljes előzményén. Tiszta függvény: ugyanabból a bemenetből
 * mindig ugyanazt adja, ezért szerkesztés, törlés és korábbi dátumra rögzítés után is újraszámolható.
 *
 * <ul>
 *   <li><b>WEEKLY_LOAD:</b> a hét eddigi edzésterhelése meghaladja az előző 4 hét átlagának küszöb-szeresét
 *       (alapértelmezetten 130%). Az átlagba az előző 4 hétből azok számítanak, amelyek az első edzés hetén vagy
 *       később vannak (az edzés nélküli hét ezek között nulla); legalább 3 ilyen hét kell az előzményhez, így az
 *       induló felhasználó nem kap hamis figyelmeztetést. Hetente egy figyelmeztetés, azzal az edzéssel, amelyik
 *       átlépte a küszöböt.</li>
 *   <li><b>MUSCLE_SETS:</b> ugyanaz az elsődleges izomcsoport három egymást követő naptári napon, naponta legalább
 *       küszöbnyi (alapértelmezetten 6) szettet kap; minden szett számít, a bemelegítő is. Sorozatonként egy
 *       figyelmeztetés, a harmadik napon.</li>
 *   <li><b>HIGH_FATIGUE:</b> három egymást követő edzés fáradtságértéke legalább a küszöb (alapértelmezetten 8).
 *       Sorozatonként egy figyelmeztetés, a harmadik edzésnél.</li>
 * </ul>
 * Csak a lezárt edzések számítanak (a fáradtság nélküli vagy nyitott edzés kimarad). A hét hétfőn kezdődik, az edzés
 * a kezdése szerinti napba tartozik, a megadott időzónában.
 */
public final class OverloadDetector {

    public static final int PREVIOUS_WEEKS = 4;
    public static final int MIN_HISTORY_WEEKS = 3;
    public static final int CONSECUTIVE_DAYS = 3;
    public static final int CONSECUTIVE_WORKOUTS = 3;

    /** A felhasználó küszöbértékei (a profilban módosíthatók). */
    public record Thresholds(BigDecimal loadRatio, int muscleSets, int fatigue) {
    }

    /** Egy megtalált figyelmeztetés; a {@code key} a szabály előfordulását azonosítja (egyedi szabályonként). */
    public record Finding(OverloadRule rule, String key, LocalDate eventDate, Long workoutId, String message) {
    }

    private OverloadDetector() {
    }

    public static List<Finding> detect(List<WorkoutRow> workouts, List<MuscleSetCount> muscleSets, Thresholds thresholds,
                                       ZoneId zone) {
        List<WorkoutRow> closed = workouts.stream()
                .filter(w -> w.endedAt() != null && w.fatigue() != null)
                .sorted(Comparator.comparing(WorkoutRow::startedAt).thenComparing(WorkoutRow::id))
                .toList();
        List<Finding> findings = new ArrayList<>();
        findings.addAll(weeklyLoad(closed, thresholds, zone));
        findings.addAll(muscleSets(muscleSets, thresholds, zone));
        findings.addAll(highFatigue(closed, thresholds, zone));
        return findings;
    }

    // ---- heti terhelés ----

    private static List<Finding> weeklyLoad(List<WorkoutRow> closed, Thresholds thresholds, ZoneId zone) {
        Map<LocalDate, BigDecimal> weekly = new TreeMap<>();
        for (WorkoutRow w : closed) {
            weekly.merge(weekOf(w, zone), loadOf(w), BigDecimal::add);
        }

        List<Finding> findings = new ArrayList<>();
        if (weekly.isEmpty()) {
            return findings;
        }
        LocalDate firstWeek = ((TreeMap<LocalDate, BigDecimal>) weekly).firstKey();
        for (LocalDate week : weekly.keySet()) {
            // előzmény: az előző 4 hét közül azok, amelyek az első edzés hetén vagy később vannak; az edzés nélküli
            // hét ezek között valódi nulla, a felhasználó indulása előtti hetek viszont nem számítanak
            BigDecimal sum = BigDecimal.ZERO;
            int historyWeeks = 0;
            for (int i = 1; i <= PREVIOUS_WEEKS; i++) {
                LocalDate previous = week.minusWeeks(i);
                if (previous.isBefore(firstWeek)) {
                    continue;
                }
                sum = sum.add(weekly.getOrDefault(previous, BigDecimal.ZERO));
                historyWeeks++;
            }
            if (historyWeeks < MIN_HISTORY_WEEKS) {
                continue;
            }
            final int weeksUsed = historyWeeks;
            BigDecimal baseline = sum.divide(BigDecimal.valueOf(historyWeeks), 1, RoundingMode.HALF_UP);
            BigDecimal limit = baseline.multiply(thresholds.loadRatio());
            if (weekly.get(week).compareTo(limit) <= 0) {
                continue;
            }
            // az az edzés, amelyik a hét eddigi összegét a küszöb fölé vitte
            BigDecimal running = BigDecimal.ZERO;
            for (WorkoutRow w : closed) {
                if (!weekOf(w, zone).equals(week)) {
                    continue;
                }
                running = running.add(loadOf(w));
                if (running.compareTo(limit) > 0) {
                    String message = String.format(
                            "A heti edzésterhelésed (%s) meghaladja az előző %d hét átlagának (%s) %s%%-át.",
                            running.setScale(0, RoundingMode.HALF_UP), weeksUsed, baseline.setScale(0, RoundingMode.HALF_UP),
                            thresholds.loadRatio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString());
                    findings.add(new Finding(OverloadRule.WEEKLY_LOAD, "week:" + week, dayOf(w, zone), w.id(), message));
                    break;
                }
            }
        }
        return findings;
    }

    private static BigDecimal loadOf(WorkoutRow w) {
        return TrainingLoad.of(w.fatigue(), TrainingLoad.minutes(w.startedAt(), w.endedAt()));
    }

    private static LocalDate dayOf(WorkoutRow w, ZoneId zone) {
        return w.startedAt().atZone(zone).toLocalDate();
    }

    private static LocalDate weekOf(WorkoutRow w, ZoneId zone) {
        return Periods.start(dayOf(w, zone), Granularity.WEEK);
    }

    // ---- izomcsoport három egymást követő napon ----

    private record DayTotal(long sets, Long lastWorkoutId, java.time.Instant lastStartedAt) {
    }

    private static List<Finding> muscleSets(List<MuscleSetCount> counts, Thresholds thresholds, ZoneId zone) {
        Map<MuscleGroup, TreeMap<LocalDate, DayTotal>> byMuscle = new EnumMap<>(MuscleGroup.class);
        for (MuscleSetCount c : counts) {
            LocalDate day = c.startedAt().atZone(zone).toLocalDate();
            byMuscle.computeIfAbsent(c.muscle(), k -> new TreeMap<>()).merge(day,
                    new DayTotal(c.sets(), c.workoutId(), c.startedAt()), OverloadDetector::mergeDays);
        }

        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<MuscleGroup, TreeMap<LocalDate, DayTotal>> entry : byMuscle.entrySet()) {
            int run = 0;
            LocalDate previousQualifying = null;
            for (Map.Entry<LocalDate, DayTotal> day : entry.getValue().entrySet()) {
                if (day.getValue().sets() < thresholds.muscleSets()) {
                    run = 0;
                    previousQualifying = null;
                    continue;
                }
                run = previousQualifying != null && previousQualifying.plusDays(1).equals(day.getKey()) ? run + 1 : 1;
                previousQualifying = day.getKey();
                if (run == CONSECUTIVE_DAYS) {
                    String message = String.format(
                            "A(z) %s izomcsoport %d egymást követő napon is legalább %d szettet kapott. Érdemes pihenőt hagyni neki.",
                            label(entry.getKey()), CONSECUTIVE_DAYS, thresholds.muscleSets());
                    findings.add(new Finding(OverloadRule.MUSCLE_SETS, "muscle:" + entry.getKey() + ":" + day.getKey(),
                            day.getKey(), day.getValue().lastWorkoutId(), message));
                }
            }
        }
        return findings;
    }

    private static DayTotal mergeDays(DayTotal a, DayTotal b) {
        boolean bIsLater = b.lastStartedAt().isAfter(a.lastStartedAt())
                || (b.lastStartedAt().equals(a.lastStartedAt()) && b.lastWorkoutId() > a.lastWorkoutId());
        DayTotal later = bIsLater ? b : a;
        return new DayTotal(a.sets() + b.sets(), later.lastWorkoutId(), later.lastStartedAt());
    }

    private static String label(MuscleGroup muscle) {
        return switch (muscle) {
            case CHEST -> "mellizom";
            case BACK -> "hát";
            case SHOULDERS -> "váll";
            case BICEPS -> "bicepsz";
            case TRICEPS -> "tricepsz";
            case FOREARMS -> "alkar";
            case ABS -> "has";
            case QUADS -> "combfeszítő";
            case HAMSTRINGS -> "combhajlító";
            case GLUTES -> "farizom";
            case CALVES -> "vádli";
            case FULL_BODY -> "egész test";
        };
    }

    // ---- magas fáradtság három egymást követő edzésen ----

    private static List<Finding> highFatigue(List<WorkoutRow> closed, Thresholds thresholds, ZoneId zone) {
        List<Finding> findings = new ArrayList<>();
        int run = 0;
        for (WorkoutRow w : closed) {
            run = w.fatigue() >= thresholds.fatigue() ? run + 1 : 0;
            if (run == CONSECUTIVE_WORKOUTS) {
                String message = String.format(
                        "Három egymást követő edzésed fáradtságértéke legalább %d. Érdemes pihenőnapot tartani.",
                        thresholds.fatigue());
                findings.add(new Finding(OverloadRule.HIGH_FATIGUE, "workout:" + w.id(), dayOf(w, zone), w.id(), message));
            }
        }
        return findings;
    }
}

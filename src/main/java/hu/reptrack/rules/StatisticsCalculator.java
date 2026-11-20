package hu.reptrack.rules;

import hu.reptrack.dto.BodyWeightPoint;
import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseProgressPoint;
import hu.reptrack.dto.GoalProgress;
import hu.reptrack.dto.GoalSnapshot;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.Granularity;
import hu.reptrack.dto.PeriodSummary;
import hu.reptrack.dto.SetRecord;
import hu.reptrack.dto.WorkoutRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A statisztikai számítások: heti/havi összesítők, gyakorlatonkénti fejlődési görbe, edzéscél-haladás.
 * Keretrendszertől és adatbázistól független, ezért egyszerűen egységtesztelhető.
 *
 * <p>Megállapodások:
 * <ul>
 *   <li>A hét hétfőn kezdődik, az edzés abba az időszakba tartozik, amelyben elkezdődött (a megadott időzónában).</li>
 *   <li>A nyitott (le nem zárt) edzés megszámolódik, de az időbe és a terhelésbe nem számít bele.</li>
 *   <li>Volumen: súly × ismétlés. Saját testsúlyos gyakorlatnál a súly a testtömeg (az edzés napján vagy előtte
 *       mért legutóbbi érték, ismeretlen esetén 0) és a külső terhelés összege. Kardió szettnek nincs volumene.</li>
 *   <li>Becsült 1RM csak súlyzós gyakorlat legfeljebb 10 ismétléses szettjéből számolódik.</li>
 * </ul>
 */
public final class StatisticsCalculator {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private StatisticsCalculator() {
    }

    // ---- összesítők ----

    /**
     * Időszakonkénti összesítő. Ha a {@code from} és a {@code to} meg van adva, a köztük lévő üres időszakok is
     * szerepelnek (nullákkal), különben az első és az utolsó adatot tartalmazó időszak közötti sorozat készül.
     */
    public static List<PeriodSummary> summarize(List<WorkoutRow> workouts, List<SetRecord> sets,
                                                List<BodyWeightPoint> bodyWeights, Granularity granularity,
                                                ZoneId zone, LocalDate from, LocalDate to) {
        TreeMap<LocalDate, Accumulator> periods = new TreeMap<>();

        for (WorkoutRow workout : workouts) {
            Accumulator acc = periods.computeIfAbsent(periodOf(workout.startedAt(), granularity, zone), k -> new Accumulator());
            acc.workouts++;
            if (workout.endedAt() != null) {
                BigDecimal minutes = TrainingLoad.minutes(workout.startedAt(), workout.endedAt());
                acc.minutes = acc.minutes.add(minutes);
                if (workout.fatigue() != null) {
                    acc.load = acc.load.add(TrainingLoad.of(workout.fatigue(), minutes));
                }
            }
        }

        for (SetRecord set : sets) {
            Accumulator acc = periods.computeIfAbsent(periodOf(set.startedAt(), granularity, zone), k -> new Accumulator());
            acc.volume = acc.volume.add(setVolume(set, bodyWeights, zone));
            if (set.distanceM() != null) {
                acc.distanceM += set.distanceM();
            }
            acc.setsByCategory.merge(set.category(), 1, Integer::sum);
        }

        LocalDate first = from != null ? Periods.start(from, granularity) : periods.isEmpty() ? null : periods.firstKey();
        LocalDate last = to != null ? Periods.start(to, granularity) : periods.isEmpty() ? null : periods.lastKey();
        List<PeriodSummary> result = new ArrayList<>();
        if (first == null || last == null) {
            return result;
        }
        for (LocalDate start = first; !start.isAfter(last); start = Periods.next(start, granularity)) {
            result.add(toSummary(start, periods.getOrDefault(start, new Accumulator())));
        }
        return result;
    }

    private static PeriodSummary toSummary(LocalDate start, Accumulator acc) {
        Map<ExerciseCategory, Integer> byCategory = new EnumMap<>(ExerciseCategory.class);
        for (ExerciseCategory category : ExerciseCategory.values()) {
            byCategory.put(category, acc.setsByCategory.getOrDefault(category, 0));
        }
        return new PeriodSummary(start, acc.workouts, acc.minutes.setScale(1, RoundingMode.HALF_UP),
                acc.volume.setScale(1, RoundingMode.HALF_UP),
                BigDecimal.valueOf(acc.distanceM).divide(THOUSAND, 2, RoundingMode.HALF_UP),
                acc.load.setScale(1, RoundingMode.HALF_UP), byCategory);
    }

    private static final class Accumulator {
        int workouts;
        BigDecimal minutes = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        BigDecimal load = BigDecimal.ZERO;
        long distanceM;
        final Map<ExerciseCategory, Integer> setsByCategory = new EnumMap<>(ExerciseCategory.class);
    }

    // ---- fejlődési görbe ----

    /**
     * Egy gyakorlat fejlődési görbéje: egy pont edzésenként, időrendben. A bemeneti szettek ugyanahhoz a gyakorlathoz
     * tartozzanak.
     */
    public static List<ExerciseProgressPoint> progress(List<SetRecord> sets, List<BodyWeightPoint> bodyWeights, ZoneId zone) {
        Map<Long, List<SetRecord>> byWorkout = new LinkedHashMap<>();
        sets.stream().sorted(Comparator.comparing(SetRecord::startedAt).thenComparing(SetRecord::workoutId))
                .forEach(s -> byWorkout.computeIfAbsent(s.workoutId(), k -> new ArrayList<>()).add(s));

        List<ExerciseProgressPoint> points = new ArrayList<>();
        for (Map.Entry<Long, List<SetRecord>> entry : byWorkout.entrySet()) {
            points.add(progressPoint(entry.getKey(), entry.getValue(), bodyWeights, zone));
        }
        return points;
    }

    private static ExerciseProgressPoint progressPoint(Long workoutId, List<SetRecord> sets,
                                                       List<BodyWeightPoint> bodyWeights, ZoneId zone) {
        SetRecord head = sets.get(0);
        LocalDate date = head.startedAt().atZone(zone).toLocalDate();

        BigDecimal bestWeight = null;
        BigDecimal best1Rm = null;
        BigDecimal volume = BigDecimal.ZERO;
        long durationSec = 0;
        long distanceM = 0;
        for (SetRecord set : sets) {
            if (set.weightKg() != null && (bestWeight == null || set.weightKg().compareTo(bestWeight) > 0)) {
                bestWeight = set.weightKg();
            }
            if (set.category() == ExerciseCategory.WEIGHTS && set.weightKg() != null && set.weightKg().signum() > 0
                    && OneRepMax.isReliable(set.reps())) {
                BigDecimal estimate = OneRepMax.estimate(set.weightKg(), set.reps());
                if (best1Rm == null || estimate.compareTo(best1Rm) > 0) {
                    best1Rm = estimate;
                }
            }
            volume = volume.add(setVolume(set, bodyWeights, zone));
            if (set.durationSec() != null && set.distanceM() != null) {
                durationSec += set.durationSec();
                distanceM += set.distanceM();
            }
        }

        BigDecimal pace = null;
        BigDecimal speed = null;
        if (head.category() == ExerciseCategory.CARDIO) {
            if (head.cardioType() == CardioType.RUNNING) {
                pace = CardioMetrics.paceMinPerKm(durationSec, distanceM);
            } else if (head.cardioType() == CardioType.CYCLING) {
                speed = CardioMetrics.speedKmh(durationSec, distanceM);
            }
        }

        BigDecimal relative = null;
        BigDecimal bodyWeight = bodyWeightOn(date, bodyWeights);
        if (best1Rm != null && bodyWeight != null && bodyWeight.signum() > 0) {
            relative = best1Rm.divide(bodyWeight, 2, RoundingMode.HALF_UP);
        }
        return new ExerciseProgressPoint(date, workoutId, bestWeight, best1Rm, volume.setScale(1, RoundingMode.HALF_UP),
                pace, speed, relative);
    }

    // ---- edzéscél ----

    /**
     * A célok haladása. Testtömeg-célnál (MUSCLE_GAIN, WEIGHT_LOSS) a célérték a cél testtömeg kg-ban, a kezdőérték a
     * cél létrehozásakor érvényes (vagy a legkorábbi) mérés. Erő-célnál (STRENGTH, gyakorlattal) a célérték a
     * gyakorlatban emelt legnagyobb súly kg-ban. ENDURANCE-re még nincs szabály, ott a százalék null.
     */
    public static List<GoalProgress> goalProgress(List<GoalSnapshot> goals, List<BodyWeightPoint> bodyWeights,
                                                  List<SetRecord> allSets) {
        List<GoalProgress> result = new ArrayList<>();
        for (GoalSnapshot goal : goals) {
            result.add(switch (goal.type()) {
                case WEIGHT_LOSS, MUSCLE_GAIN -> bodyWeightGoal(goal, bodyWeights);
                case STRENGTH -> strengthGoal(goal, allSets);
                case ENDURANCE -> new GoalProgress(goal.id(), goal.type(), goal.exerciseId(), null, null,
                        goal.targetValue(), null);
            });
        }
        return result;
    }

    private static GoalProgress bodyWeightGoal(GoalSnapshot goal, List<BodyWeightPoint> bodyWeights) {
        if (bodyWeights.isEmpty()) {
            return new GoalProgress(goal.id(), goal.type(), goal.exerciseId(), null, null, goal.targetValue(), null);
        }
        List<BodyWeightPoint> sorted = bodyWeights.stream().sorted(Comparator.comparing(BodyWeightPoint::date)).toList();
        BigDecimal current = sorted.get(sorted.size() - 1).weightKg();
        BigDecimal start = bodyWeightOn(goal.createdOn(), sorted);
        if (start == null) {
            start = sorted.get(0).weightKg();
        }
        BigDecimal percent = null;
        BigDecimal target = goal.targetValue();
        if (target != null) {
            BigDecimal total = target.subtract(start);       // előjeles: fogyásnál negatív
            BigDecimal done = current.subtract(start);
            percent = total.signum() == 0
                    ? (reached(goal.type(), current, target) ? BigDecimal.valueOf(100) : BigDecimal.ZERO)
                    : done.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
            percent = clamp(percent);
        }
        return new GoalProgress(goal.id(), goal.type(), goal.exerciseId(), start, current, target, percent);
    }

    private static boolean reached(GoalType type, BigDecimal current, BigDecimal target) {
        return type == GoalType.WEIGHT_LOSS ? current.compareTo(target) <= 0 : current.compareTo(target) >= 0;
    }

    private static GoalProgress strengthGoal(GoalSnapshot goal, List<SetRecord> allSets) {
        if (goal.exerciseId() == null) {
            return new GoalProgress(goal.id(), goal.type(), null, null, null, goal.targetValue(), null);
        }
        BigDecimal best = allSets.stream()
                .filter(s -> goal.exerciseId().equals(s.exerciseId()) && s.weightKg() != null)
                .map(SetRecord::weightKg).max(Comparator.naturalOrder()).orElse(null);
        BigDecimal percent = null;
        if (best != null && goal.targetValue() != null && goal.targetValue().signum() > 0) {
            percent = clamp(best.multiply(BigDecimal.valueOf(100)).divide(goal.targetValue(), 1, RoundingMode.HALF_UP));
        }
        return new GoalProgress(goal.id(), goal.type(), goal.exerciseId(), null, best, goal.targetValue(), percent);
    }

    private static BigDecimal clamp(BigDecimal percent) {
        if (percent.signum() < 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return percent.compareTo(BigDecimal.valueOf(100)) > 0 ? BigDecimal.valueOf(100).setScale(1) : percent;
    }

    // ---- közös segédek ----

    /** Egy szett volumene (súly × ismétlés); lásd az osztály megállapodásait. */
    static BigDecimal setVolume(SetRecord set, List<BodyWeightPoint> bodyWeights, ZoneId zone) {
        if (set.reps() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal weight;
        if (set.category() == ExerciseCategory.BODYWEIGHT) {
            BigDecimal body = bodyWeightOn(set.startedAt().atZone(zone).toLocalDate(), bodyWeights);
            weight = (body == null ? BigDecimal.ZERO : body).add(set.weightKg() == null ? BigDecimal.ZERO : set.weightKg());
        } else if (set.weightKg() != null) {
            weight = set.weightKg();
        } else {
            return BigDecimal.ZERO;
        }
        return weight.multiply(BigDecimal.valueOf(set.reps()));
    }

    /** A megadott napon vagy előtte mért legutóbbi testtömeg, ha nincs ilyen, null. */
    static BigDecimal bodyWeightOn(LocalDate date, List<BodyWeightPoint> bodyWeights) {
        BodyWeightPoint latest = null;
        for (BodyWeightPoint point : bodyWeights) {
            if (!point.date().isAfter(date) && (latest == null || point.date().isAfter(latest.date()))) {
                latest = point;
            }
        }
        return latest == null ? null : latest.weightKg();
    }

    private static LocalDate periodOf(Instant instant, Granularity granularity, ZoneId zone) {
        return Periods.start(instant.atZone(zone).toLocalDate(), granularity);
    }
}

package hu.reptrack.rules;

import hu.reptrack.dto.CardioType;
import hu.reptrack.dto.DistanceBand;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.RecordSet;
import hu.reptrack.dto.RecordType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Egyéni csúcsok (PR) felismerése egy gyakorlat szettjeiből. A szettek időrendi lejátszásával dolgozik, így
 * ugyanaz az eredmény jön ki szerkesztés, törlés és korábbi dátumra rögzítés után is (a hívó mindig az összes szettet adja).
 *
 * <p>Megállapodások:
 * <ul>
 *   <li>Rekordtípusok: legnagyobb súly adott ismétlésszámnál; becsült 1RM (csak súlyzós, legfeljebb 10 ismétlés);
 *       legnagyobb szettvolumen (súlyzós); futásnál a legjobb átlagtempó távsávonként.</li>
 *   <li>Saját testsúlyos gyakorlatnál csak a külső terhelés számít a „súly adott ismétlésszámnál” rekordba.</li>
 *   <li>PR-nak a <b>szigorúan jobb</b> eredmény számít; az egyenlő nem. Az első eredmény nem PR (nincs mihez hasonlítani).</li>
 *   <li>Egy edzésben egy rekordkulcsra csak a legjobb szett számít (azonos eredménynél a korábbi).</li>
 *   <li>A bemenetnek csak lezárt edzések szettjeit kell tartalmaznia.</li>
 * </ul>
 */
public final class PersonalRecordDetector {

    /** Egy felismert PR; a {@code setId} a rekordot elérő szett. */
    public record Found(RecordType type, Short reps, DistanceBand band, BigDecimal value, Long setId, Long workoutId,
                        LocalDate achievedOn) {
    }

    private record Key(RecordType type, Short reps, DistanceBand band) {
    }

    private record Candidate(Key key, BigDecimal value, RecordSet set) {
    }

    private PersonalRecordDetector() {
    }

    public static List<Found> detect(List<RecordSet> sets, ZoneId zone) {
        List<RecordSet> sorted = sets.stream()
                .sorted(Comparator.comparing(RecordSet::startedAt)
                        .thenComparing(RecordSet::workoutId)
                        .thenComparing(RecordSet::setId))
                .toList();
        Map<Long, List<RecordSet>> byWorkout = new LinkedHashMap<>();
        for (RecordSet set : sorted) {
            byWorkout.computeIfAbsent(set.workoutId(), k -> new ArrayList<>()).add(set);
        }

        Map<Key, BigDecimal> best = new HashMap<>();
        List<Found> found = new ArrayList<>();
        for (List<RecordSet> workoutSets : byWorkout.values()) {
            Map<Key, Candidate> bestInWorkout = new LinkedHashMap<>();
            for (RecordSet set : workoutSets) {
                for (Candidate candidate : candidates(set)) {
                    Candidate current = bestInWorkout.get(candidate.key());
                    if (current == null || isBetter(candidate.key().type(), candidate.value(), current.value())) {
                        bestInWorkout.put(candidate.key(), candidate);
                    }
                }
            }
            for (Candidate candidate : bestInWorkout.values()) {
                Key key = candidate.key();
                BigDecimal previous = best.get(key);
                if (previous == null) {
                    best.put(key, candidate.value());                      // első eredmény: alap, nem PR
                } else if (isBetter(key.type(), candidate.value(), previous)) {
                    best.put(key, candidate.value());
                    RecordSet set = candidate.set();
                    found.add(new Found(key.type(), key.reps(), key.band(), candidate.value(), set.setId(),
                            set.workoutId(), set.startedAt().atZone(zone).toLocalDate()));
                }
            }
        }
        return found;
    }

    private static boolean isBetter(RecordType type, BigDecimal candidate, BigDecimal current) {
        // a tempónál (perc/km) a kisebb érték a jobb
        return type == RecordType.BEST_PACE ? candidate.compareTo(current) < 0 : candidate.compareTo(current) > 0;
    }

    private static List<Candidate> candidates(RecordSet set) {
        List<Candidate> list = new ArrayList<>();
        BigDecimal weight = set.weightKg();
        Short reps = set.reps();
        boolean loaded = weight != null && weight.signum() > 0 && reps != null && reps >= 1;

        if (loaded && (set.category() == ExerciseCategory.WEIGHTS || set.category() == ExerciseCategory.BODYWEIGHT)) {
            list.add(candidate(RecordType.MAX_WEIGHT_AT_REPS, reps, null, weight, set));
        }
        if (loaded && set.category() == ExerciseCategory.WEIGHTS) {
            list.add(candidate(RecordType.SET_VOLUME, null, null, weight.multiply(BigDecimal.valueOf(reps)), set));
            if (OneRepMax.isReliable(reps)) {
                list.add(candidate(RecordType.ESTIMATED_1RM, null, null, OneRepMax.estimate(weight, reps), set));
            }
        }
        if (set.category() == ExerciseCategory.CARDIO && set.cardioType() == CardioType.RUNNING
                && set.durationSec() != null && set.distanceM() != null) {
            DistanceBand band = DistanceBand.forDistance(set.distanceM());
            BigDecimal pace = CardioMetrics.paceMinPerKm(set.durationSec(), set.distanceM());
            if (band != null && pace != null) {
                list.add(candidate(RecordType.BEST_PACE, null, band, pace, set));
            }
        }
        return list;
    }

    private static Candidate candidate(RecordType type, Short reps, DistanceBand band, BigDecimal value, RecordSet set) {
        return new Candidate(new Key(type, reps, band), value.setScale(2, RoundingMode.HALF_UP), set);
    }
}

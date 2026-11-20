package hu.reptrack.service;

import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.Goal;
import hu.reptrack.dto.BodyWeightPoint;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseProgressPoint;
import hu.reptrack.dto.GoalProgress;
import hu.reptrack.dto.GoalSnapshot;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.Granularity;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.PeriodSummary;
import hu.reptrack.dto.SetRecord;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.GoalRepository;
import hu.reptrack.repository.StatisticsRepository;
import hu.reptrack.rules.StatisticsCalculator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A statisztikai oldalak használati esete (UC10): heti/havi összesítők, gyakorlatonkénti fejlődési görbe,
 * testtömeg-sorozat és edzéscél-haladás. A számítás a {@link StatisticsCalculator}-ban történik, itt csak az
 * adatok lekérése, a jogosultság és a szűrők ellenőrzése van.
 */
@Service
@Transactional(readOnly = true)
public class StatisticsService {

    private final StatisticsRepository statistics;
    private final GoalRepository goals;
    private final ExerciseRepository exercises;
    private final ZoneId zone;

    public StatisticsService(StatisticsRepository statistics, GoalRepository goals, ExerciseRepository exercises,
                             @Value("${reptrack.timezone:Europe/Budapest}") String timezone) {
        this.statistics = statistics;
        this.goals = goals;
        this.exercises = exercises;
        this.zone = ZoneId.of(timezone);
    }

    /** Heti vagy havi összesítők; a {@code from} és {@code to} nap is beletartozik, bármelyik szűrő elhagyható. */
    public List<PeriodSummary> summaries(Long userId, Granularity granularity, LocalDate from, LocalDate to,
                                         Long exerciseId, ExerciseCategory category) {
        validateRange(from, to);
        if (exerciseId != null) {
            requireVisibleExercise(userId, exerciseId);
        }
        Instant start = startOf(from);
        Instant end = endOf(to);
        return StatisticsCalculator.summarize(
                statistics.findWorkouts(userId, start, end, exerciseId, category),
                statistics.findSets(userId, start, end, exerciseId, category),
                statistics.findBodyWeights(userId), granularity, zone, from, to);
    }

    /** Egy gyakorlat fejlődési görbéje: egy pont edzésenként, időrendben. */
    public List<ExerciseProgressPoint> exerciseProgress(Long userId, Long exerciseId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        requireVisibleExercise(userId, exerciseId);
        return StatisticsCalculator.progress(
                statistics.findSets(userId, startOf(from), endOf(to), exerciseId, null),
                statistics.findBodyWeights(userId), zone);
    }

    public List<BodyWeightPoint> bodyWeightSeries(Long userId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return statistics.findBodyWeights(userId).stream()
                .filter(p -> (from == null || !p.date().isBefore(from)) && (to == null || !p.date().isAfter(to)))
                .toList();
    }

    /** Az aktív edzéscélok haladása. */
    public List<GoalProgress> goalProgress(Long userId) {
        List<Goal> activeGoals = goals.findByUserIdAndActiveTrue(userId);
        List<GoalSnapshot> snapshots = new ArrayList<>();
        List<SetRecord> sets = new ArrayList<>();
        for (Goal goal : activeGoals) {
            Long exerciseId = goal.getExercise() == null ? null : goal.getExercise().getId();
            snapshots.add(new GoalSnapshot(goal.getId(), goal.getGoalType(), exerciseId, goal.getTargetValue(),
                    goal.getCreatedAt().atZone(zone).toLocalDate()));
            if (goal.getGoalType() == GoalType.STRENGTH && exerciseId != null) {
                sets.addAll(statistics.findSets(userId, null, null, exerciseId, null));
            }
        }
        return StatisticsCalculator.goalProgress(snapshots, statistics.findBodyWeights(userId), sets);
    }

    // ---- segédek ----

    private void validateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("A kezdő dátum nem lehet a záró dátum után.");
        }
    }

    private Instant startOf(LocalDate from) {
        return from == null ? null : from.atStartOfDay(zone).toInstant();
    }

    /** A záró nap is beletartozik, ezért a következő nap kezdete a kizáró határ. */
    private Instant endOf(LocalDate to) {
        return to == null ? null : to.plusDays(1).atStartOfDay(zone).toInstant();
    }

    private void requireVisibleExercise(Long userId, Long exerciseId) {
        Exercise exercise = exercises.findById(exerciseId).orElseThrow(() -> new NotFoundException("A gyakorlat nem található."));
        if (exercise.getOwner() != null && !exercise.getOwner().getId().equals(userId)) {
            throw new NotFoundException("A gyakorlat nem található.");
        }
    }
}

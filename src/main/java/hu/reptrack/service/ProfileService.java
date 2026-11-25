package hu.reptrack.service;

import hu.reptrack.domain.BodyWeightEntry;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.Goal;
import hu.reptrack.domain.User;
import hu.reptrack.dto.BodyWeightRequest;
import hu.reptrack.dto.BodyWeightView;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.EnduranceMetric;
import hu.reptrack.dto.GoalRequest;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.GoalView;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.ProfileRequest;
import hu.reptrack.dto.ProfileView;
import hu.reptrack.dto.StaleDataException;
import hu.reptrack.repository.BodyWeightEntryRepository;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.GoalRepository;
import hu.reptrack.repository.UserRepository;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * A profil, a testtömeg-napló és az edzéscélok kezelése (UC2, UC3). A testtömeg csak a naplóban él: a profil a
 * legutóbbi mérést mutatja. Minden művelet a bejelentkezett felhasználó azonosítójával dolgozik, és csak a saját
 * adatait éri el. A „ma” a beállított időzónában értendő.
 */
@Service
@Validated
@Transactional
public class ProfileService {

    private static final int MAX_AGE_YEARS = 120;

    private final UserRepository users;
    private final BodyWeightEntryRepository bodyWeights;
    private final GoalRepository goals;
    private final ExerciseRepository exercises;
    private final Clock clock;
    private final ZoneId zone;

    public ProfileService(UserRepository users, BodyWeightEntryRepository bodyWeights, GoalRepository goals,
                          ExerciseRepository exercises, Clock clock,
                          @Value("${reptrack.timezone:Europe/Budapest}") String timezone) {
        this.users = users;
        this.bodyWeights = bodyWeights;
        this.goals = goals;
        this.exercises = exercises;
        this.clock = clock;
        this.zone = ZoneId.of(timezone);
    }

    // ---- profil ----

    @Transactional(readOnly = true)
    public ProfileView profile(Long userId) {
        User user = user(userId);
        List<BodyWeightEntry> latest = bodyWeights.findByUserIdOrderByMeasuredOnDesc(userId);
        BigDecimal weight = latest.isEmpty() ? null : latest.get(0).getWeightKg();
        LocalDate weightDate = latest.isEmpty() ? null : latest.get(0).getMeasuredOn();
        return new ProfileView(user.getEmail(), user.getDisplayName(), user.getBirthDate(), user.getHeightCm(),
                user.getFitnessLevel(), user.getLoadRatioThreshold(), user.getMuscleSetsThreshold(),
                user.getFatigueThreshold(), user.getVersion(), weight, weightDate, bmi(weight, user.getHeightCm()));
    }

    /** A személyes adatok és a túlterhelési küszöbök módosítása. A {@code version} a korábban megkapott érték. */
    public void updateProfile(Long userId, @Valid ProfileRequest request) {
        User user = user(userId);
        if (!request.version().equals(user.getVersion())) {
            throw new StaleDataException("A profilt közben máshol módosították. Töltsd újra az oldalt, és próbáld újra.");
        }
        if (request.birthDate() != null && request.birthDate().isBefore(today().minusYears(MAX_AGE_YEARS))) {
            throw new BusinessRuleException("A születési dátum nem lehet " + MAX_AGE_YEARS + " évnél régebbi.");
        }
        user.setDisplayName(request.displayName().strip());
        user.setBirthDate(request.birthDate());
        user.setHeightCm(request.heightCm());
        user.setFitnessLevel(request.fitnessLevel());
        user.setLoadRatioThreshold(request.loadRatioThreshold());
        user.setMuscleSetsThreshold(request.muscleSetsThreshold());
        user.setFatigueThreshold(request.fatigueThreshold());
    }

    private static BigDecimal bmi(BigDecimal weightKg, Short heightCm) {
        if (weightKg == null || heightCm == null) {
            return null;
        }
        BigDecimal meters = BigDecimal.valueOf(heightCm).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return weightKg.divide(meters.multiply(meters), 1, RoundingMode.HALF_UP);
    }

    // ---- testtömeg-napló ----

    /** A felhasználó mérései, a legújabb elöl. */
    @Transactional(readOnly = true)
    public List<BodyWeightView> bodyWeights(Long userId) {
        return bodyWeights.findByUserIdOrderByMeasuredOnDesc(userId).stream()
                .map(e -> new BodyWeightView(e.getId(), e.getMeasuredOn(), e.getWeightKg()))
                .toList();
    }

    /** Mérés rögzítése; az adott napra már rögzített mérés felülíródik (naponta egy érték). */
    public void saveBodyWeight(Long userId, @Valid BodyWeightRequest request) {
        if (request.date().isAfter(today())) {
            throw new BusinessRuleException("A mérés dátuma nem lehet a jövőben.");
        }
        BodyWeightEntry entry = bodyWeights.findByUserIdAndMeasuredOn(userId, request.date()).orElseGet(() -> {
            BodyWeightEntry created = new BodyWeightEntry();
            created.setUser(users.getReferenceById(userId));
            created.setMeasuredOn(request.date());
            return created;
        });
        entry.setWeightKg(request.weightKg());
        bodyWeights.save(entry);
    }

    public void deleteBodyWeight(Long userId, Long id) {
        bodyWeights.delete(bodyWeights.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("A mérés nem található.")));
    }

    // ---- edzéscélok ----

    /** Az aktív célok, a legújabb elöl. */
    @Transactional(readOnly = true)
    public List<GoalView> goals(Long userId) {
        return goals.findByUserIdAndActiveTrueOrderByCreatedAtDesc(userId).stream()
                .map(g -> new GoalView(g.getId(), g.getGoalType(),
                        g.getExercise() == null ? null : g.getExercise().getId(),
                        g.getExercise() == null ? null : g.getExercise().getName(),
                        g.getTargetValue(), g.getDeadline(), g.getCreatedAt().atZone(zone).toLocalDate(),
                        g.getEnduranceMetric(), g.getTargetDistanceM()))
                .toList();
    }

    public void addGoal(Long userId, @Valid GoalRequest request) {
        GoalType type = request.type();
        boolean bodyWeightGoal = type == GoalType.WEIGHT_LOSS || type == GoalType.MUSCLE_GAIN;

        if (request.deadline() != null && request.deadline().isBefore(today())) {
            throw new BusinessRuleException("A határidő nem lehet a múltban.");
        }
        if (bodyWeightGoal && request.targetValue() != null
                && (request.targetValue().compareTo(new BigDecimal("20")) < 0
                || request.targetValue().compareTo(new BigDecimal("500")) > 0)) {
            throw new BusinessRuleException("A cél testtömeg 20 és 500 kg között lehet.");
        }
        if (type == GoalType.STRENGTH && request.targetValue() != null && request.exerciseId() == null) {
            throw new BusinessRuleException("Erő-célhoz válassz gyakorlatot, amelyben a célsúlyt el szeretnéd érni.");
        }
        EnduranceMetric metric = type == GoalType.ENDURANCE
                ? (request.enduranceMetric() == null ? EnduranceMetric.DISTANCE : request.enduranceMetric())
                : null;
        if (metric != null) {
            checkEndurancePlan(metric, request);
        }

        Goal goal = new Goal();
        goal.setUser(users.getReferenceById(userId));
        goal.setGoalType(type);
        goal.setTargetValue(request.targetValue());
        goal.setDeadline(request.deadline());
        goal.setEnduranceMetric(metric);
        goal.setTargetDistanceM(metric == EnduranceMetric.TIME_FOR_DISTANCE ? request.targetDistanceM() : null);
        if (!bodyWeightGoal && request.exerciseId() != null) {      // a testtömeg-célhoz nem tartozik gyakorlat
            Exercise exercise = exercises.findById(request.exerciseId())
                    .filter(e -> e.getOwner() == null || e.getOwner().getId().equals(userId))
                    .orElseThrow(() -> new NotFoundException("A gyakorlat nem található."));
            if (type == GoalType.STRENGTH && !exercise.isTracksWeight()) {
                throw new BusinessRuleException("Erő-célhoz olyan gyakorlat kell, amelyhez súly rögzíthető.");
            }
            if (metric != null) {
                checkEnduranceExercise(metric, exercise);
            }
            goal.setExercise(exercise);
        }
        goals.save(goal);
    }

    /**
     * Az állóképességi cél adatai: célértékhez gyakorlat is kell (különben a haladás nem számolható), az „idő adott
     * távon” célhoz pedig a táv és a célidő is.
     */
    private static void checkEndurancePlan(EnduranceMetric metric, GoalRequest request) {
        if (metric == EnduranceMetric.TIME_FOR_DISTANCE
                && (request.targetDistanceM() == null || request.targetValue() == null)) {
            throw new BusinessRuleException("Az „idő adott távon” célhoz add meg a távot és a célidőt is.");
        }
        if (request.targetValue() != null && request.exerciseId() == null) {
            throw new BusinessRuleException("Az állóképességi célhoz válassz gyakorlatot is, amelyben el szeretnéd érni.");
        }
    }

    /** A gyakorlat rögzíti-e azt, amit a cél mér (távot, időt, vagy mindkettőt). */
    private static void checkEnduranceExercise(EnduranceMetric metric, Exercise exercise) {
        boolean distanceNeeded = metric != EnduranceMetric.DURATION;
        boolean durationNeeded = metric != EnduranceMetric.DISTANCE;
        if ((distanceNeeded && !exercise.isTracksDistance()) || (durationNeeded && !exercise.isTracksDuration())) {
            String needed = switch (metric) {
                case DISTANCE -> "táv";
                case DURATION -> "időtartam";
                case TIME_FOR_DISTANCE -> "táv és időtartam";
            };
            throw new BusinessRuleException("Ehhez a célhoz olyan gyakorlat kell, amelyhez " + needed
                    + " rögzíthető; a(z) " + exercise.getName() + " ilyet nem rögzít.");
        }
    }

    public void deleteGoal(Long userId, Long id) {
        goals.delete(goals.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("A cél nem található.")));
    }

    // ---- segédek ----

    private User user(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("A felhasználó nem található."));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }
}

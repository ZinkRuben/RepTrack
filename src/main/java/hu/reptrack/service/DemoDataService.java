package hu.reptrack.service;

import hu.reptrack.dto.BodyWeightRequest;
import hu.reptrack.dto.EnduranceMetric;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseOption;
import hu.reptrack.dto.ExerciseRequest;
import hu.reptrack.dto.FitnessLevel;
import hu.reptrack.dto.GoalRequest;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.Granularity;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.OverloadWarningView;
import hu.reptrack.dto.ProfileRequest;
import hu.reptrack.dto.ProfileView;
import hu.reptrack.dto.RegistrationRequest;
import hu.reptrack.dto.TemplateExerciseRequest;
import hu.reptrack.dto.TemplateRequest;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.repository.GoalRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.rules.Periods;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demófelhasználó 16 hétnyi mintaadattal, bemutatáshoz. Induláskor a {@code DemoDataSeeder} hívja, ha a
 * {@code REPTRACK_DEMO_ENABLED} be van kapcsolva.
 *
 * <p>Minden adat a meglévő szolgáltatásokon át jön létre, így ugyanazok a szabályok érvényesek rá, mint a felületen
 * rögzített adatra, és a csúcsokat, a figyelmeztetéseket a valódi kiértékelés számolja. Az egész egy tranzakció: hiba
 * esetén semmi nem marad félkészen. A dátumok a mai naphoz igazodnak (az utolsó 16 hét, a jövőbeli edzések
 * kimaradnak), a „véletlen” ingadozás rögzített kezdőértékű.
 *
 * <p>A program: heti három edzés (hétfő: A nap, szerda: B nap, szombat: futás), fokozatos súlyemeléssel és javuló
 * futótempóval. A 7. és a 14. hét nehéz (hosszabb edzések, plusz szettek, 8–9-es fáradtság), ezek túlterhelési
 * figyelmeztetést adnak; a korábbiak nyugtázva, a legutóbbiak nyitva maradnak. A 9. hét könnyített. Emellett
 * testtömeg-napló, egy saját gyakorlat, három sablon és három cél (fogyás, erő, 5 km-es idő).
 */
@Service
@Transactional
public class DemoDataService {

    public static final String EMAIL = "demo@reptrack.com";
    /** Szándékosan nyilvános jelszó: a demófiók csak bemutatásra való (USER szerepkör, kitalált adatok). */
    public static final String PASSWORD = "demo1234";
    static final String NAME = "Demó Felhasználó";

    /** Ennyi hét adata készül; az utolsó a mostani hét (a mai napig). */
    static final int WEEKS = 16;
    /** A hetek sorszáma 0-tól: a két nehéz hét és a könnyített hét. */
    static final int HARD_WEEK_EARLY = 6;
    static final int HARD_WEEK_LATE = 13;
    static final int DELOAD_WEEK = 8;
    /** Ezeken a heteken hosszú futás van (méterben), a többin 5 km. */
    private static final Map<Integer, Integer> LONG_RUNS_M = Map.of(3, 8000, 7, 8000, 11, 8000, 15, 10000);
    private static final long SEED = 20261125L;

    static final String SQUAT = "Guggolás";
    static final String BENCH = "Fekvenyomás";
    static final String ROW = "Döntött törzsű evezés rúddal";
    static final String DEADLIFT = "Felhúzás";
    static final String PRESS = "Vállból nyomás rúddal";
    static final String PULL_UP = "Húzódzkodás";
    static final String RUN = "Futás";
    /** A demófelhasználó saját gyakorlata (UC5). */
    static final String KETTLEBELL = "Kettlebell lendítés";

    private enum Week { NORMAL, HARD, DELOAD }

    private final UserRepository users;
    private final GoalRepository goals;
    private final UserService userService;
    private final ProfileService profileService;
    private final ExerciseService exerciseService;
    private final WorkoutService workoutService;
    private final TemplateService templateService;
    private final OverloadService overloadService;
    private final Clock clock;
    private final ZoneId zone;

    public DemoDataService(UserRepository users, GoalRepository goals, UserService userService,
                           ProfileService profileService, ExerciseService exerciseService,
                           WorkoutService workoutService, TemplateService templateService,
                           OverloadService overloadService, Clock clock,
                           @Value("${reptrack.timezone:Europe/Budapest}") String timezone) {
        this.users = users;
        this.goals = goals;
        this.userService = userService;
        this.profileService = profileService;
        this.exerciseService = exerciseService;
        this.workoutService = workoutService;
        this.templateService = templateService;
        this.overloadService = overloadService;
        this.clock = clock;
        this.zone = ZoneId.of(timezone);
    }

    /**
     * A demófiók és az adatai létrehozása, ha még nincs ilyen e-mail-című fiók.
     *
     * @return igaz, ha most jött létre; hamis, ha a fiók már létezett (ilyenkor semmi nem változik)
     */
    public boolean createIfMissing() {
        if (users.existsByEmailIgnoreCase(EMAIL)) {
            return false;
        }
        Long userId = userService.register(new RegistrationRequest(NAME, EMAIL, PASSWORD, PASSWORD));
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate firstMonday = Periods.start(today, Granularity.WEEK).minusWeeks(WEEKS - 1);

        updateProfile(userId);
        Map<String, Long> ids = exerciseIds(userId, exerciseService.createOwn(userId, kettlebell()));
        logBodyWeight(userId, firstMonday, today, new Random(SEED));
        logWorkouts(userId, ids, firstMonday, new Random(SEED + 1));
        createTemplates(userId, ids);
        createGoals(userId, ids, firstMonday, today);
        acknowledgeWarningsBefore(userId, firstMonday.plusWeeks(HARD_WEEK_LATE));
        return true;
    }

    // ---- profil, gyakorlatok, testtömeg ----

    private void updateProfile(Long userId) {
        ProfileView p = profileService.profile(userId);
        profileService.updateProfile(userId, new ProfileRequest(NAME, LocalDate.of(1998, 5, 14), (short) 181,
                FitnessLevel.INTERMEDIATE, p.loadRatioThreshold(), p.muscleSetsThreshold(), p.fatigueThreshold(),
                p.version()));
    }

    private static ExerciseRequest kettlebell() {
        ExerciseRequest r = new ExerciseRequest();
        r.setName(KETTLEBELL);
        r.setCategory(ExerciseCategory.WEIGHTS);
        r.setPrimaryMuscle(MuscleGroup.GLUTES);
        r.setSecondaryMuscles(EnumSet.of(MuscleGroup.HAMSTRINGS, MuscleGroup.BACK));
        r.setDescription("Két kézzel, csípőből lendítve mellmagasságig; a hát végig egyenes.");
        r.setTracksWeight(true);
        r.setTracksReps(true);
        return r;
    }

    /** A közös gyakorlatok azonosítói név szerint, és a saját gyakorlaté. */
    private Map<String, Long> exerciseIds(Long userId, Long kettlebellId) {
        Map<String, Long> ids = new HashMap<>();
        for (ExerciseOption option : workoutService.exerciseOptions(userId, List.of())) {
            if (option.shared()) {
                ids.put(option.name(), option.id());
            }
        }
        ids.put(KETTLEBELL, kettlebellId);
        return ids;
    }

    private static Long id(Map<String, Long> ids, String name) {
        Long id = ids.get(name);
        if (id == null) {
            throw new IllegalStateException("A demóadatokhoz szükséges közös gyakorlat nem található vagy archivált: "
                    + name);
        }
        return id;
    }

    /** Hétfőn és csütörtökön reggel mérés, kb. 86,4 kg-ról 82 kg-ra csökkenő trenddel, ±0,3 kg ingadozással. */
    private void logBodyWeight(Long userId, LocalDate firstMonday, LocalDate today, Random random) {
        double totalDays = WEEKS * 7.0;
        for (int week = 0; week < WEEKS; week++) {
            for (int offset : new int[] {0, 3}) {
                LocalDate day = firstMonday.plusWeeks(week).plusDays(offset);
                if (day.isAfter(today)) {
                    return;
                }
                double trend = 86.4 - 4.2 * ChronoUnit.DAYS.between(firstMonday, day) / totalDays;
                BigDecimal kg = BigDecimal.valueOf(trend + (random.nextDouble() - 0.5) * 0.6)
                        .setScale(1, RoundingMode.HALF_UP);
                profileService.saveBodyWeight(userId, new BodyWeightRequest(day, kg));
            }
        }
    }

    // ---- edzések ----

    private void logWorkouts(Long userId, Map<String, Long> ids, LocalDate firstMonday, Random random) {
        Instant now = clock.instant();
        for (int week = 0; week < WEEKS; week++) {
            LocalDate monday = firstMonday.plusWeeks(week);
            List<WorkoutRequest> sessions = List.of(
                    dayA(ids, week, at(monday, 17, 30)),
                    dayB(ids, week, at(monday.plusDays(2), 17, 30)),
                    run(ids, week, at(monday.plusDays(5), 9, 0), random));
            for (WorkoutRequest workout : sessions) {
                if (workout.endedAt().isAfter(now)) {
                    return;
                }
                workoutService.create(userId, workout);
            }
        }
    }

    private WorkoutRequest dayA(Map<String, Long> ids, int week, Instant start) {
        Week kind = kind(week);
        int sets = kind == Week.HARD ? 4 : 3;
        return session("A nap: guggolás, fekvenyomás, evezés", start, kind, liftingMinutes(kind, 65),
                liftingFatigue(kind, 6), List.of(
                        lift(id(ids, SQUAT), sets, 5, weight(80, 2.5, week, 1, kind)),
                        lift(id(ids, BENCH), sets, 5, weight(70, 2.5, week, 2, kind)),
                        lift(id(ids, ROW), sets, 8, weight(50, 2.5, week, 2, kind))));
    }

    private WorkoutRequest dayB(Map<String, Long> ids, int week, Instant start) {
        Week kind = kind(week);
        int sets = kind == Week.HARD ? 4 : 3;
        int pullUps = kind == Week.DELOAD ? 5 : 6 + week / 3;
        double kettlebellKg = kind == Week.DELOAD || week < 6 ? 16 : week < 12 ? 20 : 24;
        return session("B nap: felhúzás, vállból nyomás, húzódzkodás", start, kind, liftingMinutes(kind, 70),
                liftingFatigue(kind, 7), List.of(
                        lift(id(ids, DEADLIFT), sets, 5, weight(100, 5, week, 2, kind)),
                        lift(id(ids, PRESS), sets, 5, weight(40, 2.5, week, 3, kind)),
                        lift(id(ids, PULL_UP), sets, pullUps, null),
                        lift(id(ids, KETTLEBELL), sets, 15, BigDecimal.valueOf(kettlebellKg).setScale(1))));
    }

    /** Nehéz héten negyedórával hosszabb, könnyített héten legfeljebb 55 perces. */
    private static int liftingMinutes(Week kind, int normal) {
        return switch (kind) {
            case NORMAL -> normal;
            case HARD -> normal + 15;
            case DELOAD -> Math.min(normal, 55);
        };
    }

    /** Nehéz héten 8–9, könnyített héten 4. */
    private static int liftingFatigue(Week kind, int normal) {
        return switch (kind) {
            case NORMAL -> normal;
            case HARD -> Math.min(normal + 2, 9);
            case DELOAD -> 4;
        };
    }

    /** Futás: az 5 km-es tempó 6:06/km-ről kb. 5:25/km-re javul; a hosszú futás lassabb, a nehéz és a könnyített hét is. */
    private WorkoutRequest run(Map<String, Long> ids, int week, Instant start, Random random) {
        Week kind = kind(week);
        int distance = LONG_RUNS_M.getOrDefault(week, 5000);
        boolean longRun = distance > 5000;
        double paceSecPerKm = 366 - 2.9 * week + (longRun ? 15 : 0) + (kind == Week.HARD ? 8 : 0)
                + (kind == Week.DELOAD ? 18 : 0) + (random.nextDouble() - 0.5) * 8;
        int seconds = (int) Math.round(paceSecPerKm * distance / 1000);
        short avgHeartRate = (short) (148 + random.nextInt(9) + (kind == Week.HARD ? 6 : 0));
        short maxHeartRate = (short) (avgHeartRate + 14 + random.nextInt(6));
        WorkoutExerciseRequest running = new WorkoutExerciseRequest(id(ids, RUN), null,
                List.of(new WorkoutSetRequest(null, null, seconds, distance, avgHeartRate, maxHeartRate)));
        int minutes = (int) Math.ceil(seconds / 60.0) + 10;                 // bemelegítéssel, levezetéssel
        int fatigue = switch (kind) {
            case NORMAL -> longRun ? 6 : 5;
            case HARD -> 8;
            case DELOAD -> 4;
        };
        String title = longRun ? "Hosszú futás, " + distance / 1000 + " km" : "Futás, 5 km";
        return session(title, start, kind, minutes, fatigue, List.of(running));
    }

    /**
     * Egy lezárt edzés. A heti edzésterhelés (fáradtság × perc) így csak a két nehéz héten lépi át az előző hetek
     * átlagának 130%-át (WEEKLY_LOAD), és csak ott van három egymást követő, legalább 8-as fáradtságú edzés
     * (HIGH_FATIGUE). Az edzések nem esnek egymást követő napokra, így izomcsoport-figyelmeztetés nincs.
     */
    private static WorkoutRequest session(String title, Instant start, Week kind, int minutes, int fatigue,
                                          List<WorkoutExerciseRequest> exercises) {
        String notes = switch (kind) {
            case NORMAL -> null;
            case HARD -> "Nehéz hét: plusz szett minden gyakorlatnál, kevés alvás.";
            case DELOAD -> "Könnyített hét: kisebb súlyok a regenerálódáshoz.";
        };
        return new WorkoutRequest(title, start, start.plus(Duration.ofMinutes(minutes)), (short) fatigue, notes, null,
                exercises);
    }

    private static WorkoutExerciseRequest lift(Long exerciseId, int sets, int reps, BigDecimal kg) {
        WorkoutSetRequest set = new WorkoutSetRequest(kg, (short) reps, null, null, null, null);
        return new WorkoutExerciseRequest(exerciseId, null, Collections.nCopies(sets, set));
    }

    /**
     * Fokozatos terhelés: {@code every} hetente {@code step} kg-mal több. Könnyített héten 80%, 2,5 kg-ra lefelé
     * kerekítve (a súly egy tizedesjegyre tárolódik, ezért 2,5 kg-os lépcsők vannak, nem 1,25-ösök).
     */
    private static BigDecimal weight(double base, double step, int week, int every, Week kind) {
        double kg = base + step * (week / every);
        if (kind == Week.DELOAD) {
            kg = Math.floor(kg * 0.8 / 2.5) * 2.5;
        }
        return BigDecimal.valueOf(kg).setScale(1, RoundingMode.HALF_UP);
    }

    private static Week kind(int week) {
        if (week == HARD_WEEK_EARLY || week == HARD_WEEK_LATE) {
            return Week.HARD;
        }
        return week == DELOAD_WEEK ? Week.DELOAD : Week.NORMAL;
    }

    private Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(zone).toInstant();
    }

    // ---- sablonok, célok, figyelmeztetések ----

    private void createTemplates(Long userId, Map<String, Long> ids) {
        templateService.create(userId, new TemplateRequest("A nap", List.of(
                planned(id(ids, SQUAT), 3, 5, "117.5"),
                planned(id(ids, BENCH), 3, 5, "87.5"),
                planned(id(ids, ROW), 3, 8, "67.5"))));
        templateService.create(userId, new TemplateRequest("B nap", List.of(
                planned(id(ids, DEADLIFT), 3, 5, "135"),
                planned(id(ids, PRESS), 3, 5, "52.5"),
                planned(id(ids, PULL_UP), 3, 11, null),
                planned(id(ids, KETTLEBELL), 3, 15, "24"))));
        templateService.create(userId, new TemplateRequest("Futás, 5 km", List.of(
                new TemplateExerciseRequest(id(ids, RUN), (short) 1, null, null, null, 5000))));
    }

    private static TemplateExerciseRequest planned(Long exerciseId, int sets, int reps, String kg) {
        return new TemplateExerciseRequest(exerciseId, (short) sets, (short) reps, kg == null ? null : new BigDecimal(kg),
                null, null);
    }

    private void createGoals(Long userId, Map<String, Long> ids, LocalDate firstMonday, LocalDate today) {
        goal(userId, new GoalRequest(GoalType.WEIGHT_LOSS, null, new BigDecimal("80"), today.plusWeeks(6)),
                firstMonday);
        goal(userId, new GoalRequest(GoalType.ENDURANCE, id(ids, RUN), new BigDecimal("25"), today.plusWeeks(8),
                EnduranceMetric.TIME_FOR_DISTANCE, 5000), firstMonday.plusWeeks(2));
        goal(userId, new GoalRequest(GoalType.STRENGTH, id(ids, BENCH), new BigDecimal("100"), today.plusWeeks(12)),
                firstMonday.plusWeeks(4));
    }

    /** A cél a szolgáltatáson át jön létre; a felvétel napját visszadátumozzuk, mert a haladás ettől a naptól számol. */
    private void goal(Long userId, GoalRequest request, LocalDate setOn) {
        Long goalId = profileService.addGoal(userId, request);
        goals.findById(goalId).orElseThrow().setCreatedAt(at(setOn, 8, 0));
    }

    /** A régebbi (az első nehéz hét) figyelmeztetései nyugtázva, a legutóbbiak nyitva maradnak. */
    private void acknowledgeWarningsBefore(Long userId, LocalDate day) {
        for (OverloadWarningView warning : overloadService.list(userId, true)) {
            if (!warning.acknowledged() && warning.eventDate().isBefore(day)) {
                overloadService.acknowledge(userId, warning.id());
            }
        }
    }
}

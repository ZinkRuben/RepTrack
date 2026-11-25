package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.User;
import hu.reptrack.dto.FitnessLevel;
import hu.reptrack.dto.GoalProgress;
import hu.reptrack.dto.Granularity;
import hu.reptrack.dto.OverloadRule;
import hu.reptrack.dto.OverloadWarningView;
import hu.reptrack.dto.PersonalRecordView;
import hu.reptrack.dto.ProfileView;
import hu.reptrack.dto.RecordType;
import hu.reptrack.dto.Role;
import hu.reptrack.repository.BodyWeightEntryRepository;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.repository.WorkoutRepository;
import hu.reptrack.repository.WorkoutTemplateRepository;
import hu.reptrack.rules.Periods;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A demófelhasználó adatai (DemoDataService). Tranzakcióban fut, így a demófiók nem marad a közös tesztadatbázisban.
 * Az adatok a mai naphoz igazodnak, ezért az elvárások a hét bármelyik napján teljesülnek. A létrehozás néhány
 * másodperc, ezért egy teszt a teljes tartalmat ellenőrzi, a másik az ismételt hívást.
 */
@Transactional
class DemoDataServiceTest extends IntegrationTestBase {

    private static final ZoneId ZONE = ZoneId.of("Europe/Budapest");

    @Autowired DemoDataService demoData;
    @Autowired UserRepository users;
    @Autowired WorkoutRepository workouts;
    @Autowired WorkoutTemplateRepository templates;
    @Autowired ExerciseRepository exercises;
    @Autowired BodyWeightEntryRepository bodyWeights;
    @Autowired ProfileService profileService;
    @Autowired PersonalRecordService personalRecords;
    @Autowired OverloadService overloadService;
    @Autowired StatisticsService statistics;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    private Long createDemo() {
        assertThat(demoData.createIfMissing()).isTrue();
        return users.findByEmailIgnoreCase(DemoDataService.EMAIL).orElseThrow().getId();
    }

    @Test
    void sixteenWeeksOfDemoDataWithRecordsWarningsAndGoals() {
        Long userId = createDemo();
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        LocalDate firstMonday = Periods.start(today, Granularity.WEEK).minusWeeks(DemoDataService.WEEKS - 1);

        // a fiók: egyszerű felhasználó a dokumentált jelszóval, kitöltött profillal
        User demo = users.findById(userId).orElseThrow();
        assertThat(demo.getRole()).isEqualTo(Role.USER);
        assertThat(demo.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches(DemoDataService.PASSWORD, demo.getPasswordHash())).isTrue();
        ProfileView profile = profileService.profile(userId);
        assertThat(profile.displayName()).isEqualTo("Demó Felhasználó");
        assertThat(profile.fitnessLevel()).isEqualTo(FitnessLevel.INTERMEDIATE);
        assertThat(profile.heightCm()).isEqualTo((short) 181);

        // heti három lezárt edzés az első hét hétfőjétől; a mostani hétből csak a már befejezettek
        assertThat(workouts.countByUserId(userId))
                .isBetween(3L * (DemoDataService.WEEKS - 1), 3L * DemoDataService.WEEKS);
        Map<String, Object> span = jdbc.queryForMap("SELECT min(started_at) AS first, max(ended_at) AS last, "
                + "count(*) FILTER (WHERE ended_at IS NULL OR fatigue IS NULL) AS open FROM workouts WHERE user_id = ?",
                userId);
        assertThat(((Timestamp) span.get("first")).toInstant().atZone(ZONE).toLocalDate()).isEqualTo(firstMonday);
        assertThat(((Timestamp) span.get("last")).toInstant()).isBeforeOrEqualTo(clock.instant());
        assertThat((Long) span.get("open")).isZero();

        // hétfőn és csütörtökön mérés a mai napig; egy saját gyakorlat, három sablon
        assertThat(bodyWeights.countByUserId(userId))
                .isBetween(2L * DemoDataService.WEEKS - 1, 2L * DemoDataService.WEEKS);
        assertThat(exercises.countByOwnerId(userId)).isEqualTo(1);
        assertThat(templates.countByUserId(userId)).isEqualTo(3);

        // a fejlődés csúcsokként látszik, az emelésekben és a futásban is
        List<PersonalRecordView> records = personalRecords.list(userId, null);
        assertThat(records).extracting(PersonalRecordView::exerciseName)
                .contains(DemoDataService.SQUAT, DemoDataService.BENCH, DemoDataService.DEADLIFT,
                        DemoDataService.KETTLEBELL, DemoDataService.RUN);
        assertThat(records).extracting(PersonalRecordView::recordType).contains(RecordType.BEST_PACE,
                RecordType.MAX_WEIGHT_AT_REPS, RecordType.ESTIMATED_1RM, RecordType.SET_VOLUME);

        // a két nehéz hét figyelmeztetései: a korábbiak nyugtázva, a legutóbbiak nyitva
        LocalDate lateHardWeek = firstMonday.plusWeeks(DemoDataService.HARD_WEEK_LATE);
        assertThat(overloadService.list(userId, true)).extracting(OverloadWarningView::rule)
                .containsExactlyInAnyOrder(OverloadRule.WEEKLY_LOAD, OverloadRule.HIGH_FATIGUE,
                        OverloadRule.WEEKLY_LOAD, OverloadRule.HIGH_FATIGUE);
        List<OverloadWarningView> open = overloadService.list(userId, false);
        assertThat(open).extracting(OverloadWarningView::rule)
                .containsExactlyInAnyOrder(OverloadRule.WEEKLY_LOAD, OverloadRule.HIGH_FATIGUE);
        assertThat(open).allSatisfy(w -> assertThat(w.eventDate()).isBetween(lateHardWeek, lateHardWeek.plusDays(6)));

        // mindhárom célnál látható, de még nem teljes a haladás
        List<GoalProgress> progress = statistics.goalProgress(userId);
        assertThat(progress).hasSize(3);
        assertThat(progress).allSatisfy(p ->
                assertThat(p.percent()).isStrictlyBetween(BigDecimal.ZERO, BigDecimal.valueOf(100)));
    }

    @Test
    void secondCallChangesNothing() {
        Long userId = createDemo();
        long workoutCount = workouts.countByUserId(userId);

        assertThat(demoData.createIfMissing()).isFalse();

        assertThat(users.findAll()).filteredOn(u -> u.getEmail().equals(DemoDataService.EMAIL)).hasSize(1);
        assertThat(workouts.countByUserId(userId)).isEqualTo(workoutCount);
    }
}

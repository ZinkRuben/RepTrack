package hu.reptrack.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.reptrack.IntegrationTestBase;
import hu.reptrack.domain.Exercise;
import hu.reptrack.domain.TemplateExercise;
import hu.reptrack.domain.User;
import hu.reptrack.domain.WorkoutTemplate;
import hu.reptrack.dto.AccountChangedEvent;
import hu.reptrack.dto.AccountDeletionSummary;
import hu.reptrack.dto.BodyWeightRequest;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseRequest;
import hu.reptrack.dto.GoalRequest;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.MuscleGroup;
import hu.reptrack.dto.PasswordChangeRequest;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.repository.WorkoutTemplateRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/** A saját fiók (AccountService): jelszó módosítása és a fiók törlése minden adatával (UC14). */
@Transactional
@RecordApplicationEvents
class AccountServiceTest extends IntegrationTestBase {

    private static final String PASSWORD = "Regi-jelszo-1";

    /** Minden tábla, amelybe a felhasználó adatai kerülnek (a users kivételével). */
    private static final List<String> DATA_TABLES = List.of("workouts", "workout_exercises", "workout_sets",
            "exercises", "exercise_secondary_muscles", "workout_templates", "template_exercises", "goals",
            "body_weight_entries", "personal_records", "overload_warnings");

    @Autowired AccountService accountService;
    @Autowired AccountDetailsService accountDetails;
    @Autowired UserRepository users;
    @Autowired ExerciseService exerciseService;
    @Autowired ExerciseRepository exercises;
    @Autowired WorkoutService workoutService;
    @Autowired ProfileService profileService;
    @Autowired WorkoutTemplateRepository templates;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired ApplicationEvents events;

    private User anna;

    @BeforeEach
    void createAnna() {
        anna = user("anna@example.com", Role.USER);
    }

    private User user(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setDisplayName(email.substring(0, email.indexOf('@')));
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRole(role);
        return users.saveAndFlush(u);
    }

    private Map<String, Long> tableCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : DATA_TABLES) {
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class));
        }
        return counts;
    }

    /** Teljes adatkészlet: saját gyakorlat, amelyre edzés, sablon és cél is hivatkozik; testtömeg; csúcsok. */
    private void fillWithData(User owner) {
        ExerciseRequest own = new ExerciseRequest();
        own.setName("Saját nyomás " + owner.getId());
        own.setCategory(ExerciseCategory.WEIGHTS);
        own.setPrimaryMuscle(MuscleGroup.CHEST);
        own.setSecondaryMuscles(Set.of(MuscleGroup.TRICEPS));
        own.setTracksWeight(true);
        own.setTracksReps(true);
        Long ownId = exerciseService.createOwn(owner.getId(), own);

        Instant start = Instant.now().minus(3, ChronoUnit.DAYS);
        for (int i = 0; i < 2; i++) {
            WorkoutSetRequest set = new WorkoutSetRequest(new BigDecimal(60 + i * 5), (short) 5, null, null, null, null);
            WorkoutRequest workout = new WorkoutRequest("Edzés " + i, start.plus(i, ChronoUnit.DAYS),
                    start.plus(i, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS), (short) 7, null, null,
                    List.of(new WorkoutExerciseRequest(ownId, null, List.of(set))));
            workoutService.create(owner.getId(), workout);
        }

        WorkoutTemplate template = new WorkoutTemplate();
        template.setUser(owner);
        template.setName("Mellnap");
        TemplateExercise te = new TemplateExercise();
        te.setExercise(em.getReference(Exercise.class, ownId));
        te.setPosition((short) 0);
        te.setPlannedReps((short) 5);
        template.addExercise(te);
        templates.save(template);

        profileService.addGoal(owner.getId(), new GoalRequest(GoalType.STRENGTH, ownId, new BigDecimal("100"), null));
        profileService.saveBodyWeight(owner.getId(), new BodyWeightRequest(LocalDate.now().minusDays(1),
                new BigDecimal("80.5")));
        em.flush();
        em.clear();
    }

    // ---- jelszó ----

    @Test
    void changesPasswordWithTheCurrentOneAndClearsTheRequiredFlag() {
        anna.setPasswordChangeRequired(true);
        users.saveAndFlush(anna);

        accountService.changePassword(anna.getId(), new PasswordChangeRequest(PASSWORD, "Uj-jelszo-12", "Uj-jelszo-12"));

        User reloaded = users.findById(anna.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("Uj-jelszo-12", reloaded.getPasswordHash())).isTrue();
        assertThat(reloaded.isPasswordChangeRequired()).isFalse();
        assertThat(accountDetails.loadUserByUsername("anna@example.com").isPasswordChangeRequired()).isFalse();
    }

    @Test
    void wrongCurrentPasswordIsRejected() {
        assertThatThrownBy(() -> accountService.changePassword(anna.getId(),
                new PasswordChangeRequest("rossz-jelszo", "Uj-jelszo-12", "Uj-jelszo-12")))
                .isInstanceOf(BusinessRuleException.class).hasMessage("A jelenlegi jelszó hibás.");
        assertThat(passwordEncoder.matches(PASSWORD, users.findById(anna.getId()).orElseThrow().getPasswordHash()))
                .isTrue();
    }

    @Test
    void newPasswordMustDifferFromTheCurrentOne() {
        assertThatThrownBy(() -> accountService.changePassword(anna.getId(),
                new PasswordChangeRequest(PASSWORD, PASSWORD, PASSWORD)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Az új jelszó nem egyezhet a jelenlegivel.");
    }

    @Test
    void newPasswordFollowsTheRegistrationRules() {
        assertThatThrownBy(() -> accountService.changePassword(anna.getId(),
                new PasswordChangeRequest(PASSWORD, "rovid", "rovid")))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> accountService.changePassword(anna.getId(),
                new PasswordChangeRequest(PASSWORD, "Uj-jelszo-12", "Uj-jelszo-13")))
                .isInstanceOf(ConstraintViolationException.class);
    }

    // ---- fiók törlése ----

    @Test
    void deletionSummaryCountsWhatWillBeDeleted() {
        fillWithData(anna);

        AccountDeletionSummary summary = accountService.deletionSummary(anna.getId());

        assertThat(summary).isEqualTo(new AccountDeletionSummary(2, 1, 1, 1, 1,
                summary.personalRecords(), false));
        assertThat(summary.personalRecords()).as("a két edzés csúcsokat hozott").isPositive();
    }

    @Test
    void deletingTheAccountRemovesEveryOwnRowAndLeavesOthersUntouched() {
        User bela = user("bela@example.com", Role.USER);
        fillWithData(bela);
        Map<String, Long> before = tableCounts();
        fillWithData(anna);
        assertThat(tableCounts()).as("Anna adatai bekerültek").isNotEqualTo(before);

        accountService.deleteOwnAccount(anna.getId(), PASSWORD);
        em.clear();

        assertThat(users.findById(anna.getId())).isEmpty();
        assertThat(tableCounts()).as("csak Anna sorai törlődtek, Béláé megmaradt").isEqualTo(before);
        assertThat(users.findById(bela.getId())).isPresent();
        assertThat(exercises.findAll()).as("a közös gyakorlatadatbázis érintetlen").anyMatch(Exercise::isShared);
        assertThat(events.stream(AccountChangedEvent.class)).containsExactly(new AccountChangedEvent(anna.getId()));
    }

    @Test
    void wrongPasswordDoesNotDeleteTheAccount() {
        assertThatThrownBy(() -> accountService.deleteOwnAccount(anna.getId(), "rossz-jelszo"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> accountService.deleteOwnAccount(anna.getId(), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(users.findById(anna.getId())).isPresent();
        assertThat(events.stream(AccountChangedEvent.class)).isEmpty();
    }

    @Test
    void theOnlyActiveAdminCannotDeleteThemselves() {
        User admin = user("admin@example.com", Role.ADMIN);
        assertThat(accountService.deletionSummary(admin.getId()).lastActiveAdmin()).isTrue();

        assertThatThrownBy(() -> accountService.deleteOwnAccount(admin.getId(), PASSWORD))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("egyetlen aktív adminisztrátor");
        assertThat(users.findById(admin.getId())).isPresent();
    }

    @Test
    void anAdminCanDeleteThemselvesWhenAnotherActiveAdminRemains() {
        User admin = user("admin@example.com", Role.ADMIN);
        User disabledAdmin = user("admin2@example.com", Role.ADMIN);
        disabledAdmin.setEnabled(false);
        users.saveAndFlush(disabledAdmin);
        assertThat(accountService.deletionSummary(admin.getId()).lastActiveAdmin())
                .as("a letiltott adminisztrátor nem számít").isTrue();

        user("admin3@example.com", Role.ADMIN);
        assertThat(accountService.deletionSummary(admin.getId()).lastActiveAdmin()).isFalse();
        accountService.deleteOwnAccount(admin.getId(), PASSWORD);
        assertThat(users.findById(admin.getId())).isEmpty();
    }
}

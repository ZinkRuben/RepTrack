package hu.reptrack.service;

import hu.reptrack.domain.User;
import hu.reptrack.dto.AccountChangedEvent;
import hu.reptrack.dto.AccountDeletionSummary;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.PasswordChangeRequest;
import hu.reptrack.repository.BodyWeightEntryRepository;
import hu.reptrack.repository.ExerciseRepository;
import hu.reptrack.repository.GoalRepository;
import hu.reptrack.repository.PersonalRecordRepository;
import hu.reptrack.repository.UserRepository;
import hu.reptrack.repository.WorkoutRepository;
import hu.reptrack.repository.WorkoutTemplateRepository;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/**
 * A bejelentkezett felhasználó saját fiókja: jelszó módosítása (a kötelező csere is, ideiglenes jelszó után) és a
 * fiók törlése minden adatával együtt (UC14). Mindkét művelet a jelenlegi jelszóval erősítendő meg, így egy nyitva
 * hagyott munkamenetből sem lehet végrehajtani őket.
 */
@Service
@Validated
@Transactional
public class AccountService {

    private final UserRepository users;
    private final WorkoutRepository workouts;
    private final ExerciseRepository exercises;
    private final WorkoutTemplateRepository templates;
    private final GoalRepository goals;
    private final BodyWeightEntryRepository bodyWeights;
    private final PersonalRecordRepository records;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    public AccountService(UserRepository users, WorkoutRepository workouts, ExerciseRepository exercises,
                          WorkoutTemplateRepository templates, GoalRepository goals,
                          BodyWeightEntryRepository bodyWeights, PersonalRecordRepository records,
                          PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
        this.users = users;
        this.workouts = workouts;
        this.exercises = exercises;
        this.templates = templates;
        this.goals = goals;
        this.bodyWeights = bodyWeights;
        this.records = records;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    /**
     * Új jelszó beállítása a jelenlegi megadásával. Az új nem egyezhet a jelenlegivel (ideiglenes jelszó után ez a
     * csere lényege). A kötelező csere jelzője törlődik.
     *
     * @throws BusinessRuleException ha a jelenlegi jelszó hibás, vagy az új megegyezik vele
     */
    public void changePassword(Long userId, @Valid PasswordChangeRequest request) {
        User user = load(userId);
        requirePassword(user, request.getCurrentPassword(), "A jelenlegi jelszó hibás.");
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Az új jelszó nem egyezhet a jelenlegivel.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangeRequired(false);
    }

    /** Mi törlődik a fiókkal együtt (a megerősítő oldalhoz), és törölheti-e egyáltalán (utolsó adminisztrátor). */
    @Transactional(readOnly = true)
    public AccountDeletionSummary deletionSummary(Long userId) {
        User user = load(userId);
        return new AccountDeletionSummary(
                workouts.countByUserId(userId),
                exercises.countByOwnerId(userId),
                templates.countByUserId(userId),
                goals.countByUserId(userId),
                bodyWeights.countByUserId(userId),
                records.countByUserId(userId),
                AdminSafeguard.isLastActiveAdmin(users, user));
    }

    /**
     * A saját fiók végleges törlése (UC14) minden adatával: edzések a szettjeikkel, saját gyakorlatok, sablonok,
     * célok, testtömeg-napló, csúcsok, figyelmeztetések (a sorrendet lásd: UserRepositoryCustomImpl). A közös
     * gyakorlatadatbázis érintetlen marad.
     *
     * @throws BusinessRuleException ha a jelszó hibás, vagy ő az utolsó aktív adminisztrátor
     */
    public void deleteOwnAccount(Long userId, String password) {
        User user = load(userId);
        requirePassword(user, password, "A jelszó hibás, a fiókot nem töröltem.");
        if (AdminSafeguard.isLastActiveAdmin(users, user)) {
            throw new BusinessRuleException("Te vagy az egyetlen aktív adminisztrátor, ezért a fiókod nem törölhető. "
                    + "Előbb nevezz ki egy másik adminisztrátort.");
        }
        users.deleteAccountWithAllData(user.getId());
        events.publishEvent(new AccountChangedEvent(userId));
    }

    private User load(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("A fiók nem található."));
    }

    private void requirePassword(User user, String password, String message) {
        if (password == null || password.isEmpty() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessRuleException(message);
        }
    }
}

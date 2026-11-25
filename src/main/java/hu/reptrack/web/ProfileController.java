package hu.reptrack.web;

import hu.reptrack.dto.BodyWeightRequest;
import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.EnduranceMetric;
import hu.reptrack.dto.FitnessLevel;
import hu.reptrack.dto.GoalProgress;
import hu.reptrack.dto.GoalRequest;
import hu.reptrack.dto.GoalType;
import hu.reptrack.dto.ProfileView;
import hu.reptrack.service.AuthenticatedUser;
import hu.reptrack.service.ProfileService;
import hu.reptrack.service.StatisticsService;
import hu.reptrack.service.WorkoutService;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** A profil oldala (UC2, UC3): személyes adatok és küszöbök, testtömeg-napló, edzéscélok a haladással. */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private static final int WEIGHTS_SHOWN = 10;
    private static final String VALIDATION_MESSAGE = "A megadott adatok érvénytelenek. Ellenőrizd az értékeket: "
            + "magasság 50–260 cm, testtömeg 20–500 kg, heti terhelés küszöb 105–300%, szettszám 1–30, fáradtság 1–10, "
            + "célérték legfeljebb 1000, céltáv legfeljebb 1000 km.";

    private final ProfileService profileService;
    private final StatisticsService statistics;
    private final WorkoutService workouts;
    private final ZoneId zone;

    public ProfileController(ProfileService profileService, StatisticsService statistics, WorkoutService workouts,
                             @Value("${reptrack.timezone:Europe/Budapest}") String timezone) {
        this.profileService = profileService;
        this.statistics = statistics;
        this.workouts = workouts;
        this.zone = ZoneId.of(timezone);
    }

    @GetMapping
    public String profile(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        ProfileView view = profileService.profile(user.getId());
        return page(user, ProfileForm.from(view), view, model);
    }

    @PostMapping
    public String update(@AuthenticationPrincipal AuthenticatedUser user, @ModelAttribute("form") ProfileForm form,
                         BindingResult binding, Model model, RedirectAttributes redirect) {
        if (!binding.hasErrors()) {
            try {
                profileService.updateProfile(user.getId(), form.toRequest());
                FlashMessages.success(redirect, "A profilod mentve.");
                return "redirect:/profile";
            } catch (BusinessRuleException | ConstraintViolationException ex) {
                model.addAttribute(FlashMessages.ERROR, messageOf(ex));
            }
        } else {
            model.addAttribute(FlashMessages.ERROR, VALIDATION_MESSAGE);
        }
        return page(user, form, profileService.profile(user.getId()), model);
    }

    // ---- testtömeg ----

    @PostMapping("/body-weight")
    public String saveBodyWeight(@AuthenticationPrincipal AuthenticatedUser user,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @RequestParam BigDecimal weightKg, RedirectAttributes redirect) {
        try {
            profileService.saveBodyWeight(user.getId(), new BodyWeightRequest(date, weightKg));
            FlashMessages.success(redirect, "A mérést rögzítettem.");
        } catch (BusinessRuleException | ConstraintViolationException ex) {
            FlashMessages.error(redirect, messageOf(ex));
        }
        return "redirect:/profile#testtomeg";
    }

    @PostMapping("/body-weight/{id}/delete")
    public String deleteBodyWeight(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                   RedirectAttributes redirect) {
        profileService.deleteBodyWeight(user.getId(), id);
        FlashMessages.success(redirect, "A mérést töröltem.");
        return "redirect:/profile#testtomeg";
    }

    // ---- célok ----

    /**
     * Új edzéscél. Állóképességi célnál a mérték is jön; az „idő adott távon” célnál a célérték a célidő
     * (perc:másodperc szövegként, percre váltva), a táv pedig km-ben (méterre váltva).
     */
    @PostMapping("/goals")
    public String addGoal(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam GoalType type,
                          @RequestParam(required = false) Long exerciseId,
                          @RequestParam(required = false) BigDecimal targetValue,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadline,
                          @RequestParam(required = false) EnduranceMetric enduranceMetric,
                          @RequestParam(required = false) BigDecimal targetDistanceKm,
                          @RequestParam(required = false) String targetTime,
                          RedirectAttributes redirect) {
        try {
            EnduranceMetric metric = type == GoalType.ENDURANCE ? enduranceMetric : null;
            BigDecimal value = targetValue;
            Integer distanceM = null;
            if (metric == EnduranceMetric.TIME_FOR_DISTANCE) {
                value = GoalDisplay.parseTime(targetTime);
                distanceM = FormValues.meters(targetDistanceKm);
            }
            profileService.addGoal(user.getId(), new GoalRequest(type, exerciseId, value, deadline, metric, distanceM));
            FlashMessages.success(redirect, "Az edzéscélt felvettem.");
        } catch (BusinessRuleException | ConstraintViolationException ex) {
            FlashMessages.error(redirect, messageOf(ex));
        }
        return "redirect:/profile#celok";
    }

    @PostMapping("/goals/{id}/delete")
    public String deleteGoal(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                             RedirectAttributes redirect) {
        profileService.deleteGoal(user.getId(), id);
        FlashMessages.success(redirect, "Az edzéscélt töröltem.");
        return "redirect:/profile#celok";
    }

    // ---- segédek ----

    private String page(AuthenticatedUser user, ProfileForm form, ProfileView view, Model model) {
        Map<Long, GoalProgress> progress = new HashMap<>();
        for (GoalProgress p : statistics.goalProgress(user.getId())) {
            progress.put(p.goalId(), p);
        }
        List<hu.reptrack.dto.BodyWeightView> weights = profileService.bodyWeights(user.getId());
        model.addAttribute("profile", view);
        model.addAttribute("form", form);
        model.addAttribute("fitnessLevels", FitnessLevel.values());
        model.addAttribute("weights", weights.stream().limit(WEIGHTS_SHOWN).toList());
        model.addAttribute("weightCount", weights.size());
        model.addAttribute("goals", profileService.goals(user.getId()).stream()
                .map(g -> GoalDisplay.of(g, progress.get(g.id()))).toList());
        model.addAttribute("goalTypes", GoalType.values());
        model.addAttribute("exerciseOptions", workouts.exerciseOptions(user.getId(), List.of()));
        model.addAttribute("today", LocalDate.now(zone));
        return "profile";
    }

    private static String messageOf(RuntimeException ex) {
        return ex instanceof ConstraintViolationException ? VALIDATION_MESSAGE : ex.getMessage();
    }
}

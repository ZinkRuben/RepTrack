package hu.reptrack.web;

import hu.reptrack.service.AuthenticatedUser;
import hu.reptrack.service.OverloadService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** A túlterhelési figyelmeztetések nyugtázása (UC12). */
@Controller
public class WarningController {

    private final OverloadService overload;

    public WarningController(OverloadService overload) {
        this.overload = overload;
    }

    @PostMapping("/warnings/{id}/acknowledge")
    public String acknowledge(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                              @RequestParam(required = false) String back, RedirectAttributes redirect) {
        overload.acknowledge(user.getId(), id);
        FlashMessages.success(redirect, "A figyelmeztetést nyugtáztad.");
        return "redirect:" + safeTarget(back);
    }

    /** Csak az edzésnapló oldalaira irányítunk vissza (nincs nyílt átirányítás idegen címre). */
    private static String safeTarget(String back) {
        return back != null && back.matches("^/workouts(/[0-9]+)?$") ? back : "/workouts";
    }
}

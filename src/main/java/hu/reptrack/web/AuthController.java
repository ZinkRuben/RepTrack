package hu.reptrack.web;

import hu.reptrack.dto.DuplicateEmailException;
import hu.reptrack.dto.RegistrationRequest;
import hu.reptrack.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Bejelentkezés és regisztráció (UC1). A bejelentkezési űrlap POST-ját (/login) és a kijelentkezést (/logout)
 * a Spring Security kezeli; ez a controller az oldalakat és a regisztrációt szolgálja ki.
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, @RequestParam(required = false) String logout,
                        @RequestParam(required = false) String expired, @RequestParam(required = false) String deleted,
                        Authentication authentication, HttpServletRequest request, Model model) {
        if (authentication != null) {
            return "redirect:/";
        }
        if (error != null) {
            model.addAttribute(FlashMessages.ERROR, loginErrorMessage(request));
        }
        if (logout != null) {
            model.addAttribute(FlashMessages.SUCCESS, "Sikeresen kijelentkeztél.");
        }
        if (expired != null) {
            // a fiókot módosították (letiltás, szerepkör, jelszó-visszaállítás, jelszócsere másik eszközön)
            model.addAttribute(FlashMessages.ERROR, "A munkameneted lejárt, mert a fiókod beállításai megváltoztak. "
                    + "Jelentkezz be újra.");
        }
        if (deleted != null) {
            model.addAttribute(FlashMessages.SUCCESS, "A fiókodat és minden adatodat töröltük.");
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Authentication authentication, Model model) {
        if (authentication != null) {
            return "redirect:/";
        }
        model.addAttribute("form", new RegistrationRequest());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegistrationRequest form, BindingResult result,
                           RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "register";
        }
        try {
            userService.register(form);
        } catch (DuplicateEmailException e) {
            result.rejectValue("email", "duplicate", e.getMessage());
            return "register";
        }
        FlashMessages.success(redirectAttributes, "Sikeres regisztráció! Most már bejelentkezhetsz.");
        redirectAttributes.addFlashAttribute("email", form.getEmail());
        return "redirect:/login";
    }

    /**
     * A sikertelen bejelentkezés oka a munkamenetben van (Spring Security). Hibás e-mail-címnél és hibás jelszónál
     * ugyanaz az üzenet, hogy ne derüljön ki, mely címek regisztráltak.
     */
    private static String loginErrorMessage(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object cause = session == null ? null : session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        if (cause instanceof DisabledException) {
            return "A fiókodat letiltották. Fordulj az adminisztrátorhoz.";
        }
        return "Hibás e-mail-cím vagy jelszó.";
    }
}

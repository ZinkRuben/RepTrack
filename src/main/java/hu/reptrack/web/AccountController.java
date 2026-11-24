package hu.reptrack.web;

import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.PasswordChangeRequest;
import hu.reptrack.service.AccountService;
import hu.reptrack.service.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * A saját fiók: jelszó módosítása (ideiglenes jelszó után kötelezően, UC13) és a fiók törlése minden adatával
 * (UC14). Mindkettőt a jelenlegi jelszó erősíti meg.
 */
@Controller
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;
    private final AccountSessions sessions;
    /** Ugyanaz, mint a Spring Security alapértelmezése: a kérés hátralévő részének és a munkamenetnek is. */
    private final SecurityContextRepository contextRepository = new DelegatingSecurityContextRepository(
            new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());

    public AccountController(AccountService accountService, AccountSessions sessions) {
        this.accountService = accountService;
        this.sessions = sessions;
    }

    // ---- jelszó ----

    @GetMapping("/password")
    public String passwordForm(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("form", new PasswordChangeRequest());
        return passwordPage(user, model);
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                 @Valid @ModelAttribute("form") PasswordChangeRequest form, BindingResult result,
                                 Model model, HttpServletRequest request, HttpServletResponse response,
                                 RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return passwordPage(user, model);
        }
        try {
            accountService.changePassword(user.getId(), form);
        } catch (BusinessRuleException e) {
            model.addAttribute(FlashMessages.ERROR, e.getMessage());
            return passwordPage(user, model);
        }
        boolean wasRequired = user.isPasswordChangeRequired();
        refreshPrincipal(user.withPasswordChanged(), request, response);
        // a többi eszközön nyitva hagyott munkamenet a régi jelszóval készült: azok lejárnak, ez megmarad
        sessions.expireAllExcept(user.getId(), request.getSession().getId());
        FlashMessages.success(redirect, wasRequired
                ? "Az új jelszavad beállítva, most már használhatod az alkalmazást."
                : "A jelszavad megváltozott. A többi eszközön újra be kell jelentkezned.");
        return wasRequired ? "redirect:/" : "redirect:/profile";
    }

    // ---- fiók törlése ----

    @GetMapping("/delete")
    public String deleteForm(@AuthenticationPrincipal AuthenticatedUser user, Model model) {
        model.addAttribute("summary", accountService.deletionSummary(user.getId()));
        return "account-delete";
    }

    @PostMapping("/delete")
    public String delete(@AuthenticationPrincipal AuthenticatedUser user,
                         @RequestParam(required = false) String password,
                         @RequestParam(required = false) boolean confirm, Model model,
                         HttpServletRequest request, HttpServletResponse response) {
        String error = null;
        if (!confirm) {
            error = "Jelöld be, hogy tudomásul veszed: a törlés végleges.";
        } else {
            try {
                accountService.deleteOwnAccount(user.getId(), password);
            } catch (BusinessRuleException e) {
                error = e.getMessage();
            }
        }
        if (error != null) {
            model.addAttribute(FlashMessages.ERROR, error);
            return deleteForm(user, model);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return "redirect:/login?deleted";
    }

    // ---- segédek ----

    private String passwordPage(AuthenticatedUser user, Model model) {
        model.addAttribute("required", user.isPasswordChangeRequired());
        return "account-password";
    }

    /** A munkamenetben lévő principal cseréje (a kötelező csere jelzője nélkül), újbóli bejelentkezés nélkül. */
    private void refreshPrincipal(AuthenticatedUser updated, HttpServletRequest request,
                                  HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(updated, null,
                updated.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}

package hu.reptrack.web;

import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.Role;
import hu.reptrack.dto.StaleDataException;
import hu.reptrack.dto.UserAdminView;
import hu.reptrack.service.AuthenticatedUser;
import hu.reptrack.service.UserAdminService;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Felhasználók kezelése (UC13): fióklista kereséssel és lapozással, letiltás és engedélyezés,
 * jelszó-visszaállítás ideiglenes jelszóval, adminisztrátor kinevezése és visszavonása, fiók törlése megerősítéssel.
 * Az /admin/** utakat a SecurityConfig csak adminisztrátornak engedi, a szolgáltatást {@code @PreAuthorize} is védi.
 *
 * <p>A műveletek a listában látott verziót küldik (optimista zárolás). Művelet után a lista ugyanarra a keresésre
 * és oldalra tér vissza.
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    static final String BASE = "/admin/users";

    private final UserAdminService admin;

    public AdminUserController(UserAdminService admin) {
        this.admin = admin;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        Page<UserAdminView> result = admin.list(q, page);
        model.addAttribute("users", result.getContent());
        model.addAttribute("q", q == null ? "" : q.strip());
        model.addAttribute("pageNumber", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("meId", me.getId());
        return "admin-users";
    }

    @GetMapping("/{id}/delete")
    public String deleteForm(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id, Model model) {
        if (id.equals(me.getId())) {
            return "redirect:/account/delete";
        }
        model.addAttribute("target", admin.get(id));
        return "admin-user-delete";
    }

    @PostMapping("/{id}/disable")
    public String disable(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                          @RequestParam(required = false) Long version, @RequestParam(required = false) String q,
                          @RequestParam(defaultValue = "0") int page, RedirectAttributes redirect) {
        return run(redirect, q, page, "A fiókot letiltottam, a felhasználó ki lett léptetve.",
                () -> admin.setEnabled(me.getId(), id, version, false));
    }

    @PostMapping("/{id}/enable")
    public String enable(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                         @RequestParam(required = false) Long version, @RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page, RedirectAttributes redirect) {
        return run(redirect, q, page, "A fiókot újra engedélyeztem.",
                () -> admin.setEnabled(me.getId(), id, version, true));
    }

    @PostMapping("/{id}/promote")
    public String promote(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                          @RequestParam(required = false) Long version, @RequestParam(required = false) String q,
                          @RequestParam(defaultValue = "0") int page, RedirectAttributes redirect) {
        return run(redirect, q, page, "Adminisztrátorrá neveztem ki; a következő bejelentkezésétől érvényes.",
                () -> admin.setRole(me.getId(), id, version, Role.ADMIN));
    }

    @PostMapping("/{id}/demote")
    public String demote(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                         @RequestParam(required = false) Long version, @RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page, RedirectAttributes redirect) {
        return run(redirect, q, page, "Az adminisztrátori jogot visszavontam, a felhasználó ki lett léptetve.",
                () -> admin.setRole(me.getId(), id, version, Role.USER));
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                                @RequestParam(required = false) Long version,
                                @RequestParam(required = false) String q,
                                @RequestParam(defaultValue = "0") int page, RedirectAttributes redirect) {
        return run(redirect, q, page, null, () -> {
            String temporary = admin.resetPassword(me.getId(), id, version);
            // csak ebben az egy válaszban jelenik meg (flash attribútum), sehol nem tárolódik
            redirect.addFlashAttribute("tempPassword", temporary);
            redirect.addFlashAttribute("tempPasswordFor", admin.get(id).email());
        });
    }

    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                         @RequestParam(required = false) Long version,
                         @RequestParam(required = false) boolean confirm, RedirectAttributes redirect) {
        if (!confirm) {
            FlashMessages.error(redirect, "Jelöld be, hogy tudomásul veszed: a törlés végleges.");
            return "redirect:" + BASE + "/" + id + "/delete";
        }
        return run(redirect, null, 0, "A fiókot és minden adatát töröltem.",
                () -> admin.delete(me.getId(), id, version));
    }

    private static String run(RedirectAttributes redirect, String q, int page, String success, Runnable action) {
        try {
            action.run();
            if (success != null) {
                FlashMessages.success(redirect, success);
            }
        } catch (BusinessRuleException | StaleDataException e) {
            FlashMessages.error(redirect, e.getMessage());
        }
        if (q != null && !q.isBlank()) {
            redirect.addAttribute("q", q.strip());
        }
        if (page > 0) {
            redirect.addAttribute("page", page);
        }
        return "redirect:" + BASE;
    }
}

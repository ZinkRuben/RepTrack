package hu.reptrack.web;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Siker- és hibaüzenetek a közös üzenetsávhoz (fragments/messages.html).
 *
 * <p>Átirányítás után (POST → redirect → GET): {@code FlashMessages.success(redirectAttributes, "Az edzés mentve.")}.
 * Azonos kérésben (pl. hibás űrlap újrarajzolása): {@code model.addAttribute(FlashMessages.ERROR, "...")}.
 */
public final class FlashMessages {

    public static final String SUCCESS = "successMessage";
    public static final String ERROR = "errorMessage";

    private FlashMessages() {
    }

    public static void success(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(SUCCESS, message);
    }

    public static void error(RedirectAttributes redirectAttributes, String message) {
        redirectAttributes.addFlashAttribute(ERROR, message);
    }
}

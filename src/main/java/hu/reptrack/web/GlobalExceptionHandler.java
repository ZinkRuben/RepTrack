package hu.reptrack.web;

import hu.reptrack.dto.BusinessRuleException;
import hu.reptrack.dto.NotFoundException;
import hu.reptrack.dto.StaleDataException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * A szolgáltatásréteg kivételeit a közös hibaoldalra (error.html) képezi le a megfelelő HTTP-státusszal, a kivétel
 * (felhasználónak szóló) üzenetével. Ami itt nincs kezelve, az a Spring Boot hibakezelésén át 500-as hibaoldal lesz,
 * belső üzenet nélkül.
 *
 * <p>Űrlapoknál a {@link BusinessRuleException}-t érdemes a controllerben elkapni, és az űrlapon megjeleníteni
 * ({@link FlashMessages#ERROR}); ide csak az el nem kapott esetek jutnak.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ModelAndView notFound(NotFoundException ex) {
        return errorPage(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(StaleDataException.class)
    public ModelAndView staleData(StaleDataException ex) {
        return errorPage(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Ritka versenyhelyzet: két mentés a verzióellenőrzés után, pontosan egyszerre ér az adatbázishoz. Ilyenkor a JPA
     * {@code @Version} mezője állítja meg a későbbit; a felhasználó ugyanazt a 409-es üzenetet kapja, mint a
     * szolgáltatás saját verzióellenőrzésénél ({@link StaleDataException}), nem az általános hibaoldalt.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ModelAndView concurrentUpdate(OptimisticLockingFailureException ex) {
        return errorPage(HttpStatus.CONFLICT,
                "Az adatot közben máshol módosították. Töltsd újra az oldalt, és mentsd újra a módosításaidat.");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ModelAndView businessRule(BusinessRuleException ex) {
        return errorPage(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private static ModelAndView errorPage(HttpStatus status, String message) {
        ModelAndView page = new ModelAndView("error", status);
        page.addObject("status", status.value());
        page.addObject("error", status.getReasonPhrase());
        page.addObject("message", message);
        return page;
    }
}

package hu.reptrack.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * A közös oldalváz adatai minden oldal modelljébe. A Thymeleaf 3.1 a sablonokból nem engedi elérni a kérést,
 * ezért az aktív menüpont kijelöléséhez az aktuális utat itt adjuk át ({@code currentPath}, a context path nélkül).
 */
@ControllerAdvice
public class LayoutModelAdvice {

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}

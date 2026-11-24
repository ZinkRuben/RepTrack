package hu.reptrack.web;

import hu.reptrack.service.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Ideiglenes jelszóval (adminisztrátori jelszó-visszaállítás után, UC13) belépett felhasználó csak a jelszócsere
 * oldalát érheti el, minden más oldal oda irányít, amíg új jelszót nem ad meg. A kijelentkezést a Spring Security
 * szűrője kezeli, azt ez nem érinti. A regisztrálást a WebConfig végzi.
 */
public class PasswordChangeInterceptor implements HandlerInterceptor {

    static final String CHANGE_PATH = "/account/password";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user && user.isPasswordChangeRequired()) {
            response.sendRedirect(request.getContextPath() + CHANGE_PATH);
            return false;
        }
        return true;
    }
}

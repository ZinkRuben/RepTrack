package hu.reptrack.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

/**
 * Regisztrációs adatok (UC1). Egyben az űrlap háttérobjektuma is, ezért JavaBean (a Thymeleaf {@code th:field}
 * gettereken és settereken keresztül olvassa és köti az értékeket). A szabályokat a webes réteg és a UserService is
 * ellenőrzi.
 */
public class RegistrationRequest {

    /** A BCrypt csak a jelszó első 72 bájtját használja, a Spring Security a hosszabbat el is utasítja. */
    public static final int MAX_PASSWORD_BYTES = 72;

    @NotBlank(message = "Add meg a neved.")
    @Size(max = 100, message = "A név legfeljebb 100 karakter lehet.")
    private String displayName;

    @NotBlank(message = "Add meg az e-mail-címed.")
    @Size(max = 255, message = "Az e-mail-cím legfeljebb 255 karakter lehet.")
    @Email(regexp = "[^@\\s]+@[^@\\s]+\\.[^@\\s]+", message = "Érvénytelen e-mail-cím.")
    private String email;

    @NotNull(message = "Add meg a jelszót.")
    @Size(min = 8, message = "A jelszó legalább 8 karakter legyen.")
    private String password;

    private String passwordConfirm;

    public RegistrationRequest() {
    }

    public RegistrationRequest(String displayName, String email, String password, String passwordConfirm) {
        setDisplayName(displayName);
        setEmail(email);
        this.password = password;
        this.passwordConfirm = passwordConfirm;
    }

    @AssertTrue(message = "A két jelszó nem egyezik.")
    public boolean isPasswordConfirmed() {
        return password == null || password.equals(passwordConfirm);
    }

    @AssertTrue(message = "A jelszó túl hosszú (legfeljebb 72 bájt; az ékezetes betűk kettőnek számítanak).")
    public boolean isPasswordWithinLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.strip();
    }

    public String getEmail() {
        return email;
    }

    /** A szóközöket levágja (bemásolt címnél gyakori), hogy az e-mail-formátum ellenőrzése ne akadjon el rajtuk. */
    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPasswordConfirm() {
        return passwordConfirm;
    }

    public void setPasswordConfirm(String passwordConfirm) {
        this.passwordConfirm = passwordConfirm;
    }
}

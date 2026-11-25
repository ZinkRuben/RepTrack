package hu.reptrack.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

/**
 * Saját jelszó módosítása (és a kötelező csere ideiglenes jelszó után). Az új jelszóra ugyanazok a szabályok
 * érvényesek, mint a regisztrációnál ({@link RegistrationRequest}). Egyben az űrlap háttérobjektuma is.
 */
public class PasswordChangeRequest {

    @NotBlank(message = "Add meg a jelenlegi jelszavad.")
    private String currentPassword;

    @NotNull(message = "Add meg az új jelszót.")
    @Size(min = 8, message = "A jelszó legalább 8 karakter legyen.")
    private String newPassword;

    private String newPasswordConfirm;

    public PasswordChangeRequest() {
    }

    public PasswordChangeRequest(String currentPassword, String newPassword, String newPasswordConfirm) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
        this.newPasswordConfirm = newPasswordConfirm;
    }

    @AssertTrue(message = "A két jelszó nem egyezik.")
    public boolean isNewPasswordConfirmed() {
        return newPassword == null || newPassword.equals(newPasswordConfirm);
    }

    @AssertTrue(message = "A jelszó túl hosszú (legfeljebb 72 bájt; az ékezetes betűk kettőnek számítanak).")
    public boolean isNewPasswordWithinLimit() {
        return newPassword == null
                || newPassword.getBytes(StandardCharsets.UTF_8).length <= RegistrationRequest.MAX_PASSWORD_BYTES;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getNewPasswordConfirm() {
        return newPasswordConfirm;
    }

    public void setNewPasswordConfirm(String newPasswordConfirm) {
        this.newPasswordConfirm = newPasswordConfirm;
    }
}

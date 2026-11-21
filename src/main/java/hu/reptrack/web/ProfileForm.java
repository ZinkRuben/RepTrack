package hu.reptrack.web;

import hu.reptrack.dto.FitnessLevel;
import hu.reptrack.dto.ProfileRequest;
import hu.reptrack.dto.ProfileView;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * A profil űrlapjának adatai. A heti terhelés küszöbét az űrlap százalékban kéri (pl. 130), a szolgáltatás
 * szorzóként (1,30) várja; a váltást a {@link #toRequest()} és a {@link #from(ProfileView)} végzi.
 */
public class ProfileForm {

    private String displayName;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;
    private Short heightCm;
    private FitnessLevel fitnessLevel;
    private Integer weeklyLoadPercent;
    private Short muscleSetsThreshold;
    private Short fatigueThreshold;
    private Long version;

    public static ProfileForm from(ProfileView view) {
        ProfileForm form = new ProfileForm();
        form.displayName = view.displayName();
        form.birthDate = view.birthDate();
        form.heightCm = view.heightCm();
        form.fitnessLevel = view.fitnessLevel();
        form.weeklyLoadPercent = view.loadRatioThreshold().multiply(BigDecimal.valueOf(100)).intValue();
        form.muscleSetsThreshold = view.muscleSetsThreshold();
        form.fatigueThreshold = view.fatigueThreshold();
        form.version = view.version();
        return form;
    }

    public ProfileRequest toRequest() {
        BigDecimal ratio = weeklyLoadPercent == null ? null
                : BigDecimal.valueOf(weeklyLoadPercent).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        return new ProfileRequest(displayName, birthDate, heightCm, fitnessLevel, ratio, muscleSetsThreshold,
                fatigueThreshold, version);
    }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public Short getHeightCm() { return heightCm; }
    public void setHeightCm(Short heightCm) { this.heightCm = heightCm; }
    public FitnessLevel getFitnessLevel() { return fitnessLevel; }
    public void setFitnessLevel(FitnessLevel fitnessLevel) { this.fitnessLevel = fitnessLevel; }
    public Integer getWeeklyLoadPercent() { return weeklyLoadPercent; }
    public void setWeeklyLoadPercent(Integer weeklyLoadPercent) { this.weeklyLoadPercent = weeklyLoadPercent; }
    public Short getMuscleSetsThreshold() { return muscleSetsThreshold; }
    public void setMuscleSetsThreshold(Short muscleSetsThreshold) { this.muscleSetsThreshold = muscleSetsThreshold; }
    public Short getFatigueThreshold() { return fatigueThreshold; }
    public void setFatigueThreshold(Short fatigueThreshold) { this.fatigueThreshold = fatigueThreshold; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

package hu.reptrack.domain;

import hu.reptrack.dto.FitnessLevel;
import hu.reptrack.dto.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String passwordHash;

    private String displayName;

    private LocalDate birthDate;

    private Short heightCm;

    @Enumerated(EnumType.STRING)
    private FitnessLevel fitnessLevel = FitnessLevel.BEGINNER;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    private boolean enabled = true;

    private BigDecimal loadRatioThreshold = new BigDecimal("1.30");

    private Short muscleSetsThreshold = 6;

    private Short fatigueThreshold = 8;

    private Instant createdAt = Instant.now();

    @Version
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Short getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Short heightCm) {
        this.heightCm = heightCm;
    }

    public FitnessLevel getFitnessLevel() {
        return fitnessLevel;
    }

    public void setFitnessLevel(FitnessLevel fitnessLevel) {
        this.fitnessLevel = fitnessLevel;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getLoadRatioThreshold() {
        return loadRatioThreshold;
    }

    public void setLoadRatioThreshold(BigDecimal loadRatioThreshold) {
        this.loadRatioThreshold = loadRatioThreshold;
    }

    public Short getMuscleSetsThreshold() {
        return muscleSetsThreshold;
    }

    public void setMuscleSetsThreshold(Short muscleSetsThreshold) {
        this.muscleSetsThreshold = muscleSetsThreshold;
    }

    public Short getFatigueThreshold() {
        return fatigueThreshold;
    }

    public void setFatigueThreshold(Short fatigueThreshold) {
        this.fatigueThreshold = fatigueThreshold;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

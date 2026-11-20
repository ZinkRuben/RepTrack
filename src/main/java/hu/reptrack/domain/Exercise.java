package hu.reptrack.domain;

import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.MuscleGroup;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "exercises")
public class Exercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    private String name;

    @Enumerated(EnumType.STRING)
    private ExerciseCategory category;

    @Enumerated(EnumType.STRING)
    private MuscleGroup primaryMuscle;

    private String secondaryMuscles;

    private String description;

    private boolean tracksWeight;

    private boolean tracksReps;

    private boolean tracksDuration;

    private boolean tracksDistance;

    private boolean tracksHeartRate;

    private boolean archived;

    @Version
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ExerciseCategory getCategory() {
        return category;
    }

    public void setCategory(ExerciseCategory category) {
        this.category = category;
    }

    public MuscleGroup getPrimaryMuscle() {
        return primaryMuscle;
    }

    public void setPrimaryMuscle(MuscleGroup primaryMuscle) {
        this.primaryMuscle = primaryMuscle;
    }

    public String getSecondaryMuscles() {
        return secondaryMuscles;
    }

    public void setSecondaryMuscles(String secondaryMuscles) {
        this.secondaryMuscles = secondaryMuscles;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isTracksWeight() {
        return tracksWeight;
    }

    public void setTracksWeight(boolean tracksWeight) {
        this.tracksWeight = tracksWeight;
    }

    public boolean isTracksReps() {
        return tracksReps;
    }

    public void setTracksReps(boolean tracksReps) {
        this.tracksReps = tracksReps;
    }

    public boolean isTracksDuration() {
        return tracksDuration;
    }

    public void setTracksDuration(boolean tracksDuration) {
        this.tracksDuration = tracksDuration;
    }

    public boolean isTracksDistance() {
        return tracksDistance;
    }

    public void setTracksDistance(boolean tracksDistance) {
        this.tracksDistance = tracksDistance;
    }

    public boolean isTracksHeartRate() {
        return tracksHeartRate;
    }

    public void setTracksHeartRate(boolean tracksHeartRate) {
        this.tracksHeartRate = tracksHeartRate;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    /** Közös (adminisztrátor által kezelt) gyakorlat: nincs tulajdonosa. */
    public boolean isShared() {
        return owner == null;
    }
}

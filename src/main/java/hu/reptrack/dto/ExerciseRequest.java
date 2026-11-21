package hu.reptrack.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gyakorlat létrehozása és módosítása (UC5, UC6). Egyben az űrlap háttérobjektuma is, ezért JavaBean.
 *
 * <p>A rögzíthető mezőkre a kategória szerinti szabályok érvényesek (ugyanazok, mint az alapadatbázisban):
 * súlyzósnál súly és ismétlés, saját testsúlyosnál ismétlés vagy időtartam (a külső terhelés opcionális),
 * kardiónál időtartam, tempónál és sebességnél táv is.
 */
public class ExerciseRequest {

    @NotBlank(message = "Add meg a gyakorlat nevét.")
    @Size(max = 120, message = "A név legfeljebb 120 karakter lehet.")
    private String name;

    @NotNull(message = "Válassz kategóriát.")
    private ExerciseCategory category;

    /** Csak kardiónál; más kategóriánál a szolgáltatás figyelmen kívül hagyja. */
    private CardioType cardioType;

    @NotNull(message = "Válassz elsődleges izomcsoportot.")
    private MuscleGroup primaryMuscle;

    /** Az elsődleges izomcsoportot a szolgáltatás kiveszi belőle, ha a felhasználó itt is bejelölte. */
    private Set<MuscleGroup> secondaryMuscles = new LinkedHashSet<>();

    @Size(max = 2000, message = "A leírás legfeljebb 2000 karakter lehet.")
    private String description;

    private boolean tracksWeight;
    private boolean tracksReps;
    private boolean tracksDuration;
    private boolean tracksDistance;
    private boolean tracksHeartRate;

    /** Módosításkor kötelező (optimista zárolás); létrehozáskor nincs. */
    private Long version;

    @AssertTrue(message = "Kardió gyakorlatnál add meg, hogyan mérjük (tempó, sebesség, vagy idő és táv).")
    public boolean isCardioTypeGiven() {
        return category != ExerciseCategory.CARDIO || cardioType != null;
    }

    @AssertTrue(message = "Legalább egy rögzíthető mezőt jelölj be.")
    public boolean isAnythingTracked() {
        return tracksWeight || tracksReps || tracksDuration || tracksDistance || tracksHeartRate;
    }

    @AssertTrue(message = "Súlyzós gyakorlatnál a súlyt és az ismétlésszámot is rögzíteni kell.")
    public boolean isWeightsTrackingValid() {
        return category != ExerciseCategory.WEIGHTS || (tracksWeight && tracksReps);
    }

    @AssertTrue(message = "Saját testsúlyos gyakorlatnál az ismétlésszámot vagy az időtartamot rögzíteni kell.")
    public boolean isBodyweightTrackingValid() {
        return category != ExerciseCategory.BODYWEIGHT || tracksReps || tracksDuration;
    }

    @AssertTrue(message = "Kardió gyakorlatnál az időtartamot rögzíteni kell, tempóhoz és sebességhez a távot is.")
    public boolean isCardioTrackingValid() {
        if (category != ExerciseCategory.CARDIO) {
            return true;
        }
        boolean needsDistance = cardioType == CardioType.RUNNING || cardioType == CardioType.CYCLING;
        return tracksDuration && (!needsDistance || tracksDistance);
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

    public CardioType getCardioType() {
        return cardioType;
    }

    public void setCardioType(CardioType cardioType) {
        this.cardioType = cardioType;
    }

    public MuscleGroup getPrimaryMuscle() {
        return primaryMuscle;
    }

    public void setPrimaryMuscle(MuscleGroup primaryMuscle) {
        this.primaryMuscle = primaryMuscle;
    }

    public Set<MuscleGroup> getSecondaryMuscles() {
        return secondaryMuscles;
    }

    public void setSecondaryMuscles(Set<MuscleGroup> secondaryMuscles) {
        this.secondaryMuscles = secondaryMuscles == null ? new LinkedHashSet<>() : secondaryMuscles;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

package hu.reptrack.web;

import hu.reptrack.dto.WorkoutExerciseRequest;
import hu.reptrack.dto.WorkoutRequest;
import hu.reptrack.dto.WorkoutSetRequest;
import hu.reptrack.dto.WorkoutView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Az edzés űrlapjának adatai. A böngészőben a felhasználó helyi idejét, percet és kilométert ad meg; a
 * {@link #toRequest(ZoneId)} ezeket alakítja a szolgáltatás nyelvére (UTC időpont, másodperc, méter), a
 * {@link #from(WorkoutView, ZoneId)} pedig vissza. A listák elemei egymás után, 0-tól indexeltek
 * ({@code exercises[0].sets[1].reps}), ezt az oldal szkriptje küldéskor rendezi.
 */
public class WorkoutForm {

    private String title;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startedAt;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endedAt;
    private Short fatigue;
    private String notes;
    private Long version;
    private List<ExerciseBlock> exercises = new ArrayList<>();

    public static class ExerciseBlock {
        private Long exerciseId;
        private String notes;
        private List<SetRow> sets = new ArrayList<>();

        public Long getExerciseId() { return exerciseId; }
        public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public List<SetRow> getSets() { return sets; }
        public void setSets(List<SetRow> sets) { this.sets = sets; }
    }

    public static class SetRow {
        private BigDecimal weightKg;
        private Short reps;
        private BigDecimal durationMin;
        private BigDecimal distanceKm;
        private Short avgHeartRate;
        private Short maxHeartRate;

        public BigDecimal getWeightKg() { return weightKg; }
        public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
        public Short getReps() { return reps; }
        public void setReps(Short reps) { this.reps = reps; }
        public BigDecimal getDurationMin() { return durationMin; }
        public void setDurationMin(BigDecimal durationMin) { this.durationMin = durationMin; }
        public BigDecimal getDistanceKm() { return distanceKm; }
        public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }
        public Short getAvgHeartRate() { return avgHeartRate; }
        public void setAvgHeartRate(Short avgHeartRate) { this.avgHeartRate = avgHeartRate; }
        public Short getMaxHeartRate() { return maxHeartRate; }
        public void setMaxHeartRate(Short maxHeartRate) { this.maxHeartRate = maxHeartRate; }
    }

    /** Új edzés kiinduló értékei: a mostani idő (percre), üres gyakorlatlistával. */
    public static WorkoutForm blank(ZoneId zone) {
        WorkoutForm form = new WorkoutForm();
        form.title = "Edzés";
        form.startedAt = LocalDateTime.now(zone).truncatedTo(ChronoUnit.MINUTES);
        return form;
    }

    public static WorkoutForm from(WorkoutView view, ZoneId zone) {
        WorkoutForm form = new WorkoutForm();
        form.title = view.title();
        form.startedAt = view.startedAt().atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES);
        form.endedAt = view.endedAt() == null ? null
                : view.endedAt().atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES);
        form.fatigue = view.fatigue();
        form.notes = view.notes();
        form.version = view.version();
        for (WorkoutView.ExerciseView e : view.exercises()) {
            ExerciseBlock block = new ExerciseBlock();
            block.exerciseId = e.exerciseId();
            block.notes = e.notes();
            for (WorkoutView.SetView s : e.sets()) {
                SetRow row = new SetRow();
                row.weightKg = s.weightKg();
                row.reps = s.reps();
                row.durationMin = FormValues.minutes(s.durationSec());
                row.distanceKm = FormValues.kilometers(s.distanceM());
                row.avgHeartRate = s.avgHeartRate();
                row.maxHeartRate = s.maxHeartRate();
                block.sets.add(row);
            }
            form.exercises.add(block);
        }
        return form;
    }

    public WorkoutRequest toRequest(ZoneId zone) {
        List<WorkoutExerciseRequest> blocks = new ArrayList<>();
        for (ExerciseBlock block : exercises) {
            List<WorkoutSetRequest> sets = new ArrayList<>();
            for (SetRow row : block.sets) {
                sets.add(new WorkoutSetRequest(row.weightKg, row.reps,
                        FormValues.seconds(row.durationMin), FormValues.meters(row.distanceKm),
                        row.avgHeartRate, row.maxHeartRate));
            }
            blocks.add(new WorkoutExerciseRequest(block.exerciseId, blankToNull(block.notes), sets));
        }
        return new WorkoutRequest(title, instant(startedAt, zone), instant(endedAt, zone), fatigue,
                blankToNull(notes), version, blocks);
    }

    private static Instant instant(LocalDateTime local, ZoneId zone) {
        return local == null ? null : local.atZone(zone).toInstant();
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public Short getFatigue() { return fatigue; }
    public void setFatigue(Short fatigue) { this.fatigue = fatigue; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public List<ExerciseBlock> getExercises() { return exercises; }
    public void setExercises(List<ExerciseBlock> exercises) { this.exercises = exercises; }
}

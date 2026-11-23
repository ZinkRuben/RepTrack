package hu.reptrack.web;

import hu.reptrack.dto.TemplateExerciseRequest;
import hu.reptrack.dto.TemplateRequest;
import hu.reptrack.dto.TemplateView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A sablon űrlapjának adatai. A böngészőben a felhasználó percet és kilométert ad meg; a {@link #toRequest()} ezeket
 * alakítja a szolgáltatás nyelvére (másodperc, méter), a {@link #from(TemplateView)} pedig vissza. A sorok 0-tól
 * egymás után indexeltek ({@code exercises[1].plannedSets}), ezt az oldal szkriptje küldéskor rendezi.
 */
public class TemplateForm {

    private String name;
    private List<Row> exercises = new ArrayList<>();

    /** Egy gyakorlat a sablonban: a szettek száma kötelező, a többi érték elhagyható. */
    public static class Row {
        private Long exerciseId;
        private Short plannedSets;
        private Short plannedReps;
        private BigDecimal plannedWeightKg;
        private BigDecimal plannedDurationMin;
        private BigDecimal plannedDistanceKm;

        public Long getExerciseId() { return exerciseId; }
        public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }
        public Short getPlannedSets() { return plannedSets; }
        public void setPlannedSets(Short plannedSets) { this.plannedSets = plannedSets; }
        public Short getPlannedReps() { return plannedReps; }
        public void setPlannedReps(Short plannedReps) { this.plannedReps = plannedReps; }
        public BigDecimal getPlannedWeightKg() { return plannedWeightKg; }
        public void setPlannedWeightKg(BigDecimal plannedWeightKg) { this.plannedWeightKg = plannedWeightKg; }
        public BigDecimal getPlannedDurationMin() { return plannedDurationMin; }
        public void setPlannedDurationMin(BigDecimal plannedDurationMin) { this.plannedDurationMin = plannedDurationMin; }
        public BigDecimal getPlannedDistanceKm() { return plannedDistanceKm; }
        public void setPlannedDistanceKm(BigDecimal plannedDistanceKm) { this.plannedDistanceKm = plannedDistanceKm; }
    }

    public static TemplateForm from(TemplateView view) {
        TemplateForm form = new TemplateForm();
        form.name = view.name();
        for (TemplateView.ExerciseView e : view.exercises()) {
            Row row = new Row();
            row.exerciseId = e.exerciseId();
            row.plannedSets = e.plannedSets();
            row.plannedReps = e.plannedReps();
            row.plannedWeightKg = e.plannedWeightKg();
            row.plannedDurationMin = FormValues.minutes(e.plannedDurationSec());
            row.plannedDistanceKm = FormValues.kilometers(e.plannedDistanceM());
            form.exercises.add(row);
        }
        return form;
    }

    public TemplateRequest toRequest() {
        List<TemplateExerciseRequest> rows = new ArrayList<>();
        for (Row row : exercises) {
            rows.add(new TemplateExerciseRequest(row.exerciseId, row.plannedSets, row.plannedReps, row.plannedWeightKg,
                    FormValues.seconds(row.plannedDurationMin), FormValues.meters(row.plannedDistanceKm)));
        }
        return new TemplateRequest(name, rows);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Row> getExercises() { return exercises; }
    public void setExercises(List<Row> exercises) { this.exercises = exercises; }
}

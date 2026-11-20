package hu.reptrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "template_exercises")
public class TemplateExercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id")
    private WorkoutTemplate template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    private Short position;

    private Short plannedSets = 3;

    private Short plannedReps;

    private BigDecimal plannedWeightKg;

    private Integer plannedDurationSec;

    @Column(name = "planned_distance_m")
    private Integer plannedDistanceM;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public WorkoutTemplate getTemplate() {
        return template;
    }

    public void setTemplate(WorkoutTemplate template) {
        this.template = template;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Short getPosition() {
        return position;
    }

    public void setPosition(Short position) {
        this.position = position;
    }

    public Short getPlannedSets() {
        return plannedSets;
    }

    public void setPlannedSets(Short plannedSets) {
        this.plannedSets = plannedSets;
    }

    public Short getPlannedReps() {
        return plannedReps;
    }

    public void setPlannedReps(Short plannedReps) {
        this.plannedReps = plannedReps;
    }

    public Integer getPlannedDurationSec() {
        return plannedDurationSec;
    }

    public void setPlannedDurationSec(Integer plannedDurationSec) {
        this.plannedDurationSec = plannedDurationSec;
    }

    public Integer getPlannedDistanceM() {
        return plannedDistanceM;
    }

    public void setPlannedDistanceM(Integer plannedDistanceM) {
        this.plannedDistanceM = plannedDistanceM;
    }

    public BigDecimal getPlannedWeightKg() {
        return plannedWeightKg;
    }

    public void setPlannedWeightKg(BigDecimal plannedWeightKg) {
        this.plannedWeightKg = plannedWeightKg;
    }
}

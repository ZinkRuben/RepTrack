package hu.reptrack.web;

import hu.reptrack.dto.BodyWeightPoint;
import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseOption;
import hu.reptrack.dto.ExerciseProgressPoint;
import hu.reptrack.dto.Granularity;
import hu.reptrack.dto.PeriodSummary;
import hu.reptrack.dto.PersonalRecordView;
import hu.reptrack.rules.Periods;
import hu.reptrack.service.AuthenticatedUser;
import hu.reptrack.service.PersonalRecordService;
import hu.reptrack.service.StatisticsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * A statisztikai oldal (UC10): heti/havi összesítők, kategóriánkénti megoszlás, egy gyakorlat fejlődési görbéje az
 * egyéni csúcsok jelölésével, és a testtömeg alakulása. A szűrők GET paraméterek. A diagramok adatait egyszerű
 * struktúrákban (listák, számok) adjuk a Chart.js-t használó szkriptnek.
 */
@Controller
public class StatsController {

    private static final int DEFAULT_PERIODS = 12;
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("MM. dd.");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy. MM.");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("yyyy. MM. dd.");

    private final StatisticsService statistics;
    private final PersonalRecordService personalRecords;
    private final ZoneId zone;

    public StatsController(StatisticsService statistics, PersonalRecordService personalRecords,
                           @Value("${reptrack.timezone:Europe/Budapest}") String timezone) {
        this.statistics = statistics;
        this.personalRecords = personalRecords;
        this.zone = ZoneId.of(timezone);
    }

    @GetMapping("/stats")
    public String stats(@AuthenticationPrincipal AuthenticatedUser user,
                        @RequestParam(defaultValue = "WEEK") Granularity granularity,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(required = false) Long exerciseId, Model model) {
        LocalDate today = LocalDate.now(zone);
        if (from != null && to != null && from.isAfter(to)) {
            model.addAttribute(FlashMessages.ERROR, "A kezdő dátum nem lehet a záró dátum után, az alapértelmezett időszakot mutatom.");
            from = null;
            to = null;
        }
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : defaultStart(end, granularity);

        List<PeriodSummary> summaries = statistics.summaries(user.getId(), granularity, start, end, null, null);
        List<ExerciseOption> used = statistics.usedExercises(user.getId());

        model.addAttribute("granularity", granularity);
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("exerciseId", exerciseId);
        model.addAttribute("usedExercises", used);
        model.addAttribute("totals", totals(summaries));
        model.addAttribute("summaryData", summaryData(summaries, granularity));
        model.addAttribute("progressData", exerciseId == null ? null : progressData(user.getId(), exerciseId, used));
        model.addAttribute("bodyWeightData", bodyWeightData(user.getId()));
        return "stats";
    }

    private static LocalDate defaultStart(LocalDate end, Granularity granularity) {
        LocalDate periodStart = Periods.start(end, granularity);
        return granularity == Granularity.WEEK
                ? periodStart.minusWeeks(DEFAULT_PERIODS - 1L)
                : periodStart.minusMonths(DEFAULT_PERIODS - 1L);
    }

    // ---- az időszak összesen ----

    /** Az időszak teljes összege a kártyákhoz. */
    public record Totals(int workouts, BigDecimal minutes, BigDecimal volumeKg, BigDecimal distanceKm, BigDecimal load) {
    }

    private static Totals totals(List<PeriodSummary> summaries) {
        int workouts = 0;
        BigDecimal minutes = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        BigDecimal distance = BigDecimal.ZERO;
        BigDecimal load = BigDecimal.ZERO;
        for (PeriodSummary s : summaries) {
            workouts += s.workouts();
            minutes = minutes.add(s.totalMinutes());
            volume = volume.add(s.totalVolumeKg());
            distance = distance.add(s.distanceKm());
            load = load.add(s.trainingLoad());
        }
        return new Totals(workouts, minutes, volume, distance, load);
    }

    // ---- a diagramok adatai (a szkriptnek) ----

    private Map<String, Object> summaryData(List<PeriodSummary> summaries, Granularity granularity) {
        DateTimeFormatter label = granularity == Granularity.WEEK ? WEEK_LABEL : MONTH_LABEL;
        List<String> labels = new ArrayList<>();
        List<Integer> workouts = new ArrayList<>();
        List<Double> minutes = new ArrayList<>();
        List<Double> volume = new ArrayList<>();
        List<Double> distance = new ArrayList<>();
        List<Double> load = new ArrayList<>();
        int weights = 0;
        int cardio = 0;
        int bodyweight = 0;
        for (PeriodSummary s : summaries) {
            labels.add(label.format(s.periodStart()));
            workouts.add(s.workouts());
            minutes.add(s.totalMinutes().doubleValue());
            volume.add(s.totalVolumeKg().doubleValue());
            distance.add(s.distanceKm().doubleValue());
            load.add(s.trainingLoad().doubleValue());
            weights += s.setsByCategory().get(ExerciseCategory.WEIGHTS);
            cardio += s.setsByCategory().get(ExerciseCategory.CARDIO);
            bodyweight += s.setsByCategory().get(ExerciseCategory.BODYWEIGHT);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", labels);
        data.put("workouts", workouts);
        data.put("minutes", minutes);
        data.put("volume", volume);
        data.put("distance", distance);
        data.put("load", load);
        data.put("categoryLabels", List.of("Súlyzós", "Kardió", "Saját testsúlyos"));
        data.put("categorySets", List.of(weights, cardio, bodyweight));
        return data;
    }

    private Map<String, Object> progressData(Long userId, Long exerciseId, List<ExerciseOption> used) {
        List<ExerciseProgressPoint> points = statistics.exerciseProgress(userId, exerciseId, null, null);
        Set<Long> recordWorkouts = new HashSet<>();
        for (PersonalRecordView r : personalRecords.list(userId, exerciseId)) {
            if (r.workoutId() != null) {
                recordWorkouts.add(r.workoutId());
            }
        }
        List<String> labels = new ArrayList<>();
        List<Double> best = new ArrayList<>();
        List<Double> oneRm = new ArrayList<>();
        List<Double> volume = new ArrayList<>();
        List<Double> pace = new ArrayList<>();
        List<Double> speed = new ArrayList<>();
        List<Double> relative = new ArrayList<>();
        List<Boolean> record = new ArrayList<>();
        for (ExerciseProgressPoint p : points) {
            labels.add(DAY_LABEL.format(p.date()));
            best.add(number(p.bestWeightKg()));
            oneRm.add(number(p.estimated1Rm()));
            volume.add(number(p.volumeKg()));
            pace.add(number(p.paceMinPerKm()));
            speed.add(number(p.speedKmh()));
            relative.add(number(p.relativeStrength()));
            record.add(recordWorkouts.contains(p.workoutId()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", used.stream().filter(o -> o.id().equals(exerciseId)).map(ExerciseOption::name).findFirst().orElse(""));
        data.put("labels", labels);
        data.put("bestWeight", best);
        data.put("estimated1Rm", oneRm);
        data.put("volume", volume);
        data.put("pace", pace);
        data.put("speed", speed);
        data.put("relativeStrength", relative);
        data.put("record", record);
        return data;
    }

    private Map<String, Object> bodyWeightData(Long userId) {
        List<BodyWeightPoint> points = statistics.bodyWeightSeries(userId, null, null);
        if (points.isEmpty()) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("labels", points.stream().map(p -> DAY_LABEL.format(p.date())).toList());
        data.put("values", points.stream().map(p -> number(p.weightKg())).toList());
        return data;
    }

    private static Double number(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}

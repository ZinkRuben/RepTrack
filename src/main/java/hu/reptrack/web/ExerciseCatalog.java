package hu.reptrack.web;

import hu.reptrack.dto.ExerciseCategory;
import hu.reptrack.dto.ExerciseOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A gyakorlatválasztó adatai az űrlapok szkriptjének (edzés és sablon): azonosító, név, kategória, és hogy mely mezők
 * rögzíthetők (w: súly, r: ismétlés, d: időtartam, k: táv, h: pulzus). Az oldal JSON-ként ágyazza be.
 */
final class ExerciseCatalog {

    private static final Map<ExerciseCategory, String> CATEGORY_LABELS = Map.of(
            ExerciseCategory.WEIGHTS, "Súlyzós",
            ExerciseCategory.CARDIO, "Kardió",
            ExerciseCategory.BODYWEIGHT, "Saját testsúlyos");

    private ExerciseCatalog() {
    }

    static List<Map<String, Object>> of(List<ExerciseOption> options) {
        return options.stream().map(ExerciseCatalog::entry).toList();
    }

    private static Map<String, Object> entry(ExerciseOption o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(o.id()));
        m.put("name", o.name());
        m.put("category", o.category().name());
        m.put("categoryLabel", CATEGORY_LABELS.get(o.category()));
        m.put("w", o.tracksWeight());
        m.put("r", o.tracksReps());
        m.put("d", o.tracksDuration());
        m.put("k", o.tracksDistance());
        m.put("h", o.tracksHeartRate());
        m.put("shared", o.shared());
        return m;
    }
}

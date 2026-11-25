package hu.reptrack.web;

import hu.reptrack.dto.ExerciseOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * A gyakorlatválasztó adatai az űrlapok szkriptjének (edzés és sablon, js/exercise-picker.js): azonosító, név,
 * kategória és elsődleges izomcsoport (magyar felirattal, a szűrőkhöz), a kép címe (ha van), és hogy mely mezők
 * rögzíthetők (w: súly, r: ismétlés, d: időtartam, k: táv, h: pulzus). Az oldal JSON-ként ágyazza be.
 */
@Component
class ExerciseCatalog {

    private final ExerciseImages images;
    private final MessageSource messages;

    ExerciseCatalog(ExerciseImages images, MessageSource messages) {
        this.images = images;
        this.messages = messages;
    }

    List<Map<String, Object>> of(List<ExerciseOption> options) {
        return options.stream().map(this::entry).toList();
    }

    private Map<String, Object> entry(ExerciseOption o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(o.id()));
        m.put("name", o.name());
        m.put("category", o.category().name());
        m.put("categoryLabel", label("category." + o.category().name()));
        m.put("muscle", o.primaryMuscle() == null ? null : o.primaryMuscle().name());
        m.put("muscleLabel", o.primaryMuscle() == null ? null : label("muscle." + o.primaryMuscle().name()));
        m.put("image", images.urlFor(o.imageKey()));
        m.put("w", o.tracksWeight());
        m.put("r", o.tracksReps());
        m.put("d", o.tracksDuration());
        m.put("k", o.tracksDistance());
        m.put("h", o.tracksHeartRate());
        m.put("shared", o.shared());
        return m;
    }

    private String label(String code) {
        return messages.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }
}

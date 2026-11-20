package hu.reptrack.service;

import hu.reptrack.domain.Exercise;
import hu.reptrack.dto.ExerciseFilter;
import hu.reptrack.dto.ExerciseView;
import hu.reptrack.repository.ExerciseRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A gyakorlat-adatbázis böngészése (UC4): keresés névre, szűrés kategóriára és izomcsoportra.
 *
 * <p>Egyelőre a közös gyakorlatokat listázza; a felhasználó saját gyakorlatai (UC5) a valódi bejelentkezéssel
 * kerülnek mellé, akkor a metódus a felhasználó azonosítóját is megkapja.
 */
@Service
@Transactional(readOnly = true)
public class ExerciseService {

    private final ExerciseRepository exercises;

    public ExerciseService(ExerciseRepository exercises) {
        this.exercises = exercises;
    }

    public List<ExerciseView> search(ExerciseFilter filter) {
        return exercises.searchShared(namePattern(filter.name()), filter.category(), filter.muscle()).stream()
                .map(ExerciseService::toView)
                .toList();
    }

    /** Részszöveg-keresés mintája; az üres név nem szűr, a LIKE helyettesítő karakterei szó szerint értendők. */
    private static String namePattern(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String escaped = name.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static ExerciseView toView(Exercise e) {
        return new ExerciseView(
                e.getId(), e.getName(), e.getCategory(), e.getPrimaryMuscle(),
                e.getSecondaryMuscles().stream().sorted().toList(),
                e.getCardioType(), e.getDescription(),
                e.isTracksWeight(), e.isTracksReps(), e.isTracksDuration(), e.isTracksDistance(), e.isTracksHeartRate());
    }
}

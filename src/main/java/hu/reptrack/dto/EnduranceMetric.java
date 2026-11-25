package hu.reptrack.dto;

/**
 * Az állóképességi cél (ENDURANCE) mértéke (goals.endurance_metric). A célérték egysége ettől függ.
 *
 * <ul>
 *   <li>{@link #DISTANCE}: táv egy edzésen (szetten), km-ben; pl. „fuss 10 km-t egyben”.</li>
 *   <li>{@link #DURATION}: idő egy edzésen (szetten), percben; pl. „ússz 45 percet egyben”.</li>
 *   <li>{@link #TIME_FOR_DISTANCE}: idő adott távon, percben (a táv külön, méterben); pl. „5 km 25:00 alatt”.</li>
 * </ul>
 */
public enum EnduranceMetric {
    DISTANCE, DURATION, TIME_FOR_DISTANCE
}

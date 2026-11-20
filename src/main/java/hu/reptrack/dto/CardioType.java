package hu.reptrack.dto;

/**
 * Kardió gyakorlat fajtája (exercises.cardio_type), csak CARDIO kategóriánál van kitöltve.
 * RUNNING: tempó (perc/km) és tempó-PR; CYCLING: sebesség (km/h); OTHER (pl. úszás): csak időtartam és távolság.
 */
public enum CardioType {
    RUNNING, CYCLING, OTHER
}

package hu.reptrack.dto;

/** Az adatot közben más módosította (optimista zárolás): a mentés nem írja felül a másik módosítását. */
public class StaleDataException extends RuntimeException {

    public StaleDataException(String message) {
        super(message);
    }
}

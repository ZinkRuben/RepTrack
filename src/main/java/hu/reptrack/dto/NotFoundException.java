package hu.reptrack.dto;

/**
 * A keresett adat nem létezik, vagy a bejelentkezett felhasználó nem láthatja
 * (a kettőt szándékosan nem különböztetjük meg, hogy más adatának létezése ne derüljön ki).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}

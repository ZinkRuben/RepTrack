package hu.reptrack.dto;

/**
 * Távsáv a futási tempó-PR-hez (personal_records.distance_band). Egy szett a legnagyobb olyan sávba tartozik, amelynek
 * távja nem nagyobb a szett távolságánál (pl. 6 km-es futás az 5K sávba, 900 m-es egyikbe sem).
 */
public enum DistanceBand {
    ONE_K(1_000), FIVE_K(5_000), TEN_K(10_000), HALF_MARATHON(21_097), MARATHON(42_195);

    private final int meters;

    DistanceBand(int meters) {
        this.meters = meters;
    }

    public int meters() {
        return meters;
    }

    /** A szett távolságához tartozó sáv, vagy null, ha a távolság az 1 km-es sávnál is rövidebb. */
    public static DistanceBand forDistance(int distanceM) {
        DistanceBand result = null;
        for (DistanceBand band : values()) {      // növekvő sorrendben
            if (distanceM >= band.meters) {
                result = band;
            }
        }
        return result;
    }
}

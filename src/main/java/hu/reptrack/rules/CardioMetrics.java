package hu.reptrack.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Kardió mérőszámok: tempó (perc/km) futáshoz, sebesség (km/h) kerékpárhoz. */
public final class CardioMetrics {

    private CardioMetrics() {
    }

    /** Átlagtempó percben kilométerenként; null, ha az időtartam vagy a távolság nem pozitív. */
    public static BigDecimal paceMinPerKm(long durationSec, long distanceM) {
        if (durationSec <= 0 || distanceM <= 0) {
            return null;
        }
        return BigDecimal.valueOf(durationSec * 1000L)
                .divide(BigDecimal.valueOf(60L * distanceM), 2, RoundingMode.HALF_UP);
    }

    /** Átlagsebesség km/h-ban; null, ha az időtartam vagy a távolság nem pozitív. */
    public static BigDecimal speedKmh(long durationSec, long distanceM) {
        if (durationSec <= 0 || distanceM <= 0) {
            return null;
        }
        return BigDecimal.valueOf(distanceM * 36L)
                .divide(BigDecimal.valueOf(durationSec * 10L), 1, RoundingMode.HALF_UP);
    }
}

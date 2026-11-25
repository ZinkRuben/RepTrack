package hu.reptrack.web;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Az űrlapok közös mértékegység-átváltásai és szövegalakjai (edzés és sablon). A böngészőben a felhasználó percet és
 * kilométert ad meg, a szolgáltatás másodpercet és métert tárol.
 */
final class FormValues {

    private FormValues() {
    }

    /** Perc → másodperc (egészre kerekítve). */
    static Integer seconds(BigDecimal minutes) {
        return scaled(minutes, 60);
    }

    /** Kilométer → méter (egészre kerekítve). */
    static Integer meters(BigDecimal kilometers) {
        return scaled(kilometers, 1000);
    }

    /** Másodperc → perc (két tizedesre). */
    static BigDecimal minutes(Integer seconds) {
        return seconds == null ? null
                : BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    /** Méter → kilométer (három tizedesre). */
    static BigDecimal kilometers(Integer meters) {
        return meters == null ? null
                : BigDecimal.valueOf(meters).divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
    }

    /** Beviteli mező értéke (a számmezők tizedespontot várnak, a felesleges nullák nélkül). */
    static String text(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : String.valueOf(value);
    }

    /** Megjelenítendő szám magyarul: tizedesvessző, a felesleges nullák nélkül (pl. 82,5). */
    static String display(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static Integer scaled(BigDecimal value, int factor) {
        return value == null ? null
                : value.multiply(BigDecimal.valueOf(factor)).setScale(0, RoundingMode.HALF_UP).intValue();
    }
}

package hu.reptrack.web;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Számok megjelenítése a sablonokban magyar formában: tizedesvessző, a felesleges nullák nélkül (82,5; 100). A
 * sablonokból {@code ${@num.of(érték)}} alakban hívható. A beviteli mezők értéke nem ezzel készül, mert a
 * számmezők tizedespontot várnak.
 */
@Component("num")
public class NumberDisplay {

    public String of(BigDecimal value) {
        return FormValues.display(value);
    }
}

package hu.reptrack.dto;

/** Üzleti szabály megsértése (pl. nem rögzíthető mező, érvénytelen időpontok). Az üzenet a felhasználónak szól. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}

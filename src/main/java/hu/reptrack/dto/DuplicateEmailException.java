package hu.reptrack.dto;

/** Regisztrációkor: ezzel az e-mail-címmel (kis- és nagybetűtől függetlenül) már van fiók. */
public class DuplicateEmailException extends BusinessRuleException {

    public DuplicateEmailException() {
        super("Ezzel az e-mail-címmel már regisztráltak.");
    }
}

package hu.reptrack.dto;

/** A felhasználónak már van ilyen nevű sablonja (kis- és nagybetűtől függetlenül). */
public class DuplicateTemplateNameException extends BusinessRuleException {
    public DuplicateTemplateNameException() {
        super("Ilyen nevű sablonod már van. Adj meg másik nevet.");
    }
}

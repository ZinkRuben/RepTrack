package hu.reptrack.dto;

/**
 * Egy fiók biztonsági szempontból megváltozott (letiltás, szerepkör-változás, jelszó-visszaállítás, törlés). A
 * szolgáltatásréteg teszi közzé a sikeres tranzakcióban; a webes réteg erre lejáratja a fiók munkameneteit, így a
 * változás azonnal érvényes, nem csak a következő bejelentkezéskor. (A szolgáltatások így nem tudnak a
 * munkamenetekről, azok csak a webes rétegben élnek.)
 */
public record AccountChangedEvent(Long userId) {
}

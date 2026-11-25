-- UC13: az adminisztrátor által visszaállított (ideiglenes) jelszó után a felhasználónak a következő
-- bejelentkezéskor új jelszót kell megadnia. A szám azért V8, mert a feature/uc15 és a feature/small-fixes ág
-- V5–V7-et már használja.
ALTER TABLE users ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;

-- Túlterhelési figyelmeztetések: stabil azonosító és esemény dátuma.
-- A rule_key azonosítja, hogy a szabály melyik előfordulásáról van szó (pl. 'week:2026-11-16'), így az újraszámolás
-- nem hoz létre duplikátumot, és a már nyugtázott figyelmeztetés nyugtázott marad.
ALTER TABLE overload_warnings ADD COLUMN rule_key VARCHAR(80) NOT NULL DEFAULT '';
ALTER TABLE overload_warnings ADD COLUMN event_date DATE NOT NULL DEFAULT current_date;
ALTER TABLE overload_warnings ALTER COLUMN rule_key DROP DEFAULT;
ALTER TABLE overload_warnings ALTER COLUMN event_date DROP DEFAULT;
CREATE UNIQUE INDEX ux_overload_warnings_rule_key ON overload_warnings (user_id, rule_code, rule_key);

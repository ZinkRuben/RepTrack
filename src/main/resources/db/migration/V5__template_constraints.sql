-- Edzéssablonok (UC15): a tervezett értékek ugyanazokkal a korlátokkal, mint a rögzített szettek (workout_sets),
-- a sablon neve felhasználónként egyedi (kis- és nagybetűtől függetlenül), és indexek a gyakori lekérdezésekhez.
ALTER TABLE template_exercises
    ADD CONSTRAINT ck_template_exercises_sets CHECK (planned_sets BETWEEN 1 AND 20);

ALTER TABLE template_exercises
    ADD CONSTRAINT ck_template_exercises_reps CHECK (planned_reps BETWEEN 1 AND 100);

ALTER TABLE template_exercises
    ADD CONSTRAINT ck_template_exercises_weight CHECK (planned_weight_kg BETWEEN 0 AND 500);

CREATE UNIQUE INDEX ux_workout_templates_name ON workout_templates (user_id, lower(name));
CREATE INDEX idx_template_exercises_template ON template_exercises(template_id);
CREATE INDEX idx_template_exercises_exercise ON template_exercises(exercise_id);

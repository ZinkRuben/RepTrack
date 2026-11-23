-- Állóképességi célok (ENDURANCE) mértéke:
--   DISTANCE           táv egy edzésen (célérték: km), pl. 10 km egyben
--   DURATION           idő egy edzésen (célérték: perc), pl. 45 perc úszás
--   TIME_FOR_DISTANCE  idő adott távon (célérték: perc, a táv a target_distance_m-ben), pl. 5 km 25:00 alatt
-- A többi céltípusnál mindkét új oszlop üres.
ALTER TABLE goals ADD COLUMN endurance_metric VARCHAR(20);
ALTER TABLE goals ADD COLUMN target_distance_m INTEGER;

-- a meglévő állóképességi célok: távcél, ha a gyakorlat távot rögzít, különben időcél
UPDATE goals g
SET endurance_metric = CASE WHEN e.tracks_distance THEN 'DISTANCE' ELSE 'DURATION' END
FROM exercises e
WHERE g.goal_type = 'ENDURANCE' AND g.exercise_id = e.id;

UPDATE goals SET endurance_metric = 'DISTANCE' WHERE goal_type = 'ENDURANCE' AND endurance_metric IS NULL;

ALTER TABLE goals
    ADD CONSTRAINT ck_goals_endurance_metric CHECK (endurance_metric IN ('DISTANCE','DURATION','TIME_FOR_DISTANCE'));

-- a mérték pontosan az állóképességi célnál van megadva; a táv csak az „idő adott távon” célhoz tartozik, ott kötelező
ALTER TABLE goals
    ADD CONSTRAINT ck_goals_endurance_shape CHECK (
        (goal_type = 'ENDURANCE' AND endurance_metric IS NOT NULL
            AND (endurance_metric = 'TIME_FOR_DISTANCE') = (target_distance_m IS NOT NULL))
        OR (goal_type <> 'ENDURANCE' AND endurance_metric IS NULL AND target_distance_m IS NULL));

ALTER TABLE goals
    ADD CONSTRAINT ck_goals_target_distance CHECK (target_distance_m > 0);

-- Egyéni csúcsok: a távsáv értékei és a rekordtípusonként kitöltendő mezők ellenőrzése.
ALTER TABLE personal_records
    ADD CONSTRAINT ck_personal_records_band
        CHECK (distance_band IN ('ONE_K','FIVE_K','TEN_K','HALF_MARATHON','MARATHON'));

ALTER TABLE personal_records
    ADD CONSTRAINT ck_personal_records_shape CHECK (
        (record_type = 'MAX_WEIGHT_AT_REPS' AND reps IS NOT NULL AND distance_band IS NULL)
        OR (record_type = 'BEST_PACE' AND distance_band IS NOT NULL AND reps IS NULL)
        OR (record_type IN ('ESTIMATED_1RM', 'SET_VOLUME') AND reps IS NULL AND distance_band IS NULL));

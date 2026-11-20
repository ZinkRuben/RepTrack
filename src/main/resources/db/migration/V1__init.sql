-- RepTrack kezdő adatbázisséma (JAVASLAT: a csapat közösen véglegesíti, mielőtt a rétegek erre épülnek)

CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    email                VARCHAR(255) NOT NULL UNIQUE,
    password_hash        VARCHAR(100) NOT NULL,
    display_name         VARCHAR(100) NOT NULL,
    birth_date           DATE,
    height_cm            SMALLINT CHECK (height_cm BETWEEN 50 AND 260),
    fitness_level        VARCHAR(20)  NOT NULL DEFAULT 'BEGINNER',   -- BEGINNER / INTERMEDIATE / ADVANCED
    role                 VARCHAR(20)  NOT NULL DEFAULT 'USER',       -- USER / ADMIN
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    -- túlterhelési küszöbök (a profilban módosíthatók)
    load_ratio_threshold NUMERIC(4,2) NOT NULL DEFAULT 1.30,
    muscle_sets_threshold SMALLINT    NOT NULL DEFAULT 6,
    fatigue_threshold    SMALLINT     NOT NULL DEFAULT 8,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version              BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE body_weight_entries (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    measured_on DATE          NOT NULL,
    weight_kg   NUMERIC(5,1)  NOT NULL CHECK (weight_kg BETWEEN 20 AND 500),
    UNIQUE (user_id, measured_on)
);

CREATE TABLE exercises (
    id                BIGSERIAL PRIMARY KEY,
    owner_id          BIGINT REFERENCES users(id) ON DELETE CASCADE,  -- NULL = közös gyakorlat
    name              VARCHAR(120) NOT NULL,
    category          VARCHAR(20)  NOT NULL,                          -- WEIGHTS / CARDIO / BODYWEIGHT
    primary_muscle    VARCHAR(40),
    secondary_muscles VARCHAR(200),
    description       TEXT,
    tracks_weight     BOOLEAN NOT NULL DEFAULT FALSE,
    tracks_reps       BOOLEAN NOT NULL DEFAULT FALSE,
    tracks_duration   BOOLEAN NOT NULL DEFAULT FALSE,
    tracks_distance   BOOLEAN NOT NULL DEFAULT FALSE,
    tracks_heart_rate BOOLEAN NOT NULL DEFAULT FALSE,
    archived          BOOLEAN NOT NULL DEFAULT FALSE,
    version           BIGINT  NOT NULL DEFAULT 0
);
CREATE INDEX idx_exercises_owner ON exercises(owner_id);
CREATE INDEX idx_exercises_category ON exercises(category);

CREATE TABLE goals (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_type    VARCHAR(20)  NOT NULL,                  -- MUSCLE_GAIN / WEIGHT_LOSS / ENDURANCE / STRENGTH
    exercise_id  BIGINT REFERENCES exercises(id),        -- pl. guggolás célhoz
    target_value NUMERIC(8,2),                           -- pl. 80 (kg) vagy 100 (kg)
    deadline     DATE,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE workouts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(120) NOT NULL,
    started_at TIMESTAMPTZ  NOT NULL,
    ended_at   TIMESTAMPTZ,
    fatigue    SMALLINT CHECK (fatigue BETWEEN 1 AND 10),
    notes      TEXT,
    version    BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_workouts_user_started ON workouts(user_id, started_at);

CREATE TABLE workout_exercises (
    id          BIGSERIAL PRIMARY KEY,
    workout_id  BIGINT   NOT NULL REFERENCES workouts(id) ON DELETE CASCADE,
    exercise_id BIGINT   NOT NULL REFERENCES exercises(id),
    position    SMALLINT NOT NULL,
    notes       TEXT
);
CREATE INDEX idx_workout_exercises_workout ON workout_exercises(workout_id);
CREATE INDEX idx_workout_exercises_exercise ON workout_exercises(exercise_id);

CREATE TABLE workout_sets (
    id                  BIGSERIAL PRIMARY KEY,
    workout_exercise_id BIGINT   NOT NULL REFERENCES workout_exercises(id) ON DELETE CASCADE,
    position            SMALLINT NOT NULL,
    weight_kg           NUMERIC(5,1) CHECK (weight_kg BETWEEN 0 AND 500),  -- saját testsúlyosnál külső terhelés
    reps                SMALLINT     CHECK (reps BETWEEN 1 AND 100),
    duration_sec        INTEGER      CHECK (duration_sec > 0),
    distance_m          INTEGER      CHECK (distance_m > 0),
    avg_heart_rate      SMALLINT     CHECK (avg_heart_rate BETWEEN 30 AND 230),
    max_heart_rate      SMALLINT     CHECK (max_heart_rate BETWEEN 30 AND 230)
);
CREATE INDEX idx_workout_sets_we ON workout_sets(workout_exercise_id);

CREATE TABLE workout_templates (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name    VARCHAR(120) NOT NULL
);

CREATE TABLE template_exercises (
    id              BIGSERIAL PRIMARY KEY,
    template_id     BIGINT   NOT NULL REFERENCES workout_templates(id) ON DELETE CASCADE,
    exercise_id     BIGINT   NOT NULL REFERENCES exercises(id),
    position        SMALLINT NOT NULL,
    planned_sets    SMALLINT NOT NULL DEFAULT 3,
    planned_reps    SMALLINT,
    planned_weight_kg NUMERIC(5,1)
);

CREATE TABLE personal_records (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    exercise_id    BIGINT       NOT NULL REFERENCES exercises(id),
    record_type    VARCHAR(30)  NOT NULL,   -- MAX_WEIGHT_AT_REPS / ESTIMATED_1RM / SET_VOLUME / BEST_PACE
    reps           SMALLINT,                -- MAX_WEIGHT_AT_REPS esetén
    distance_band  VARCHAR(20),             -- BEST_PACE esetén (pl. 5K, 10K)
    value          NUMERIC(10,2) NOT NULL,
    workout_set_id BIGINT REFERENCES workout_sets(id) ON DELETE CASCADE,
    achieved_on    DATE         NOT NULL
);
CREATE INDEX idx_pr_user_exercise ON personal_records(user_id, exercise_id);

CREATE TABLE overload_warnings (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    workout_id   BIGINT REFERENCES workouts(id) ON DELETE CASCADE,
    rule_code    VARCHAR(30)  NOT NULL,     -- WEEKLY_LOAD / MUSCLE_SETS / HIGH_FATIGUE
    message      VARCHAR(300) NOT NULL,
    acknowledged BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_warnings_user ON overload_warnings(user_id, acknowledged);

-- Baseline schema RoomBook (T1, T2)

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Location hierarchy (A1): self-referencing, number of levels not fixed
CREATE TABLE location
(
    location_id        bigint GENERATED ALWAYS AS IDENTITY,
    parent_location_id bigint,
    name               varchar(100) NOT NULL,
    type               varchar(20)  NOT NULL,
    active             boolean      NOT NULL DEFAULT true,
    CONSTRAINT pk_location PRIMARY KEY (location_id),
    CONSTRAINT fk_location_location FOREIGN KEY (parent_location_id) REFERENCES location (location_id),
    CONSTRAINT ck_location_type CHECK (type IN ('BRANCH', 'BUILDING', 'FLOOR')),
    CONSTRAINT ck_location_parent_location_id CHECK (parent_location_id <> location_id)
);

CREATE INDEX idx_location_parent_location_id ON location (parent_location_id);

-- Cycle protection (A1): the new parent must not have the node itself as an ancestor
CREATE FUNCTION location_check_cycle() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    -- serializes hierarchy changes, otherwise two concurrent updates (A→B, B→A) could jointly form a cycle
    PERFORM pg_advisory_xact_lock(hashtext('location_hierarchy'));

    IF EXISTS (WITH RECURSIVE ancestors (location_id) AS (SELECT NEW.parent_location_id
                                                          UNION
                                                          SELECT l.parent_location_id
                                                          FROM location l
                                                                   JOIN ancestors a ON l.location_id = a.location_id
                                                          WHERE l.parent_location_id IS NOT NULL)
               SELECT 1
               FROM ancestors
               WHERE location_id = NEW.location_id) THEN
        RAISE EXCEPTION 'location % cannot be moved below %: cycle in hierarchy', NEW.location_id, NEW.parent_location_id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'ck_location_no_cycle';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER location_no_cycle
    BEFORE UPDATE OF parent_location_id
    ON location
    FOR EACH ROW
    WHEN (NEW.parent_location_id IS NOT NULL AND NEW.parent_location_id IS DISTINCT FROM OLD.parent_location_id)
EXECUTE FUNCTION location_check_cycle();

-- Rooms (A2)
CREATE TABLE room
(
    room_id     bigint GENERATED ALWAYS AS IDENTITY,
    location_id bigint       NOT NULL,
    name        varchar(100) NOT NULL,
    capacity    integer      NOT NULL,
    active      boolean      NOT NULL DEFAULT true,
    CONSTRAINT pk_room PRIMARY KEY (room_id),
    CONSTRAINT fk_room_location FOREIGN KEY (location_id) REFERENCES location (location_id),
    CONSTRAINT ck_room_capacity CHECK (capacity > 0),
    CONSTRAINT uq_room_location_id_name UNIQUE (location_id, name)
);

-- Users (mocked auth via X-User-Id)
CREATE TABLE app_user
(
    app_user_id bigint GENERATED ALWAYS AS IDENTITY,
    username    varchar(50) NOT NULL,
    role        varchar(20) NOT NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (app_user_id),
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'ADMIN'))
);

-- Booking series (A4): weekly, max. 12 occurrences
CREATE TABLE booking_series
(
    booking_series_id bigint GENERATED ALWAYS AS IDENTITY,
    app_user_id       bigint      NOT NULL,
    rule              varchar(20) NOT NULL,
    occurrences       integer     NOT NULL,
    CONSTRAINT pk_booking_series PRIMARY KEY (booking_series_id),
    CONSTRAINT fk_booking_series_app_user FOREIGN KEY (app_user_id) REFERENCES app_user (app_user_id),
    CONSTRAINT ck_booking_series_rule CHECK (rule IN ('WEEKLY')),
    CONSTRAINT ck_booking_series_occurrences CHECK (occurrences BETWEEN 1 AND 12)
);

-- Bookings (A4, A5)
CREATE TABLE booking
(
    booking_id        bigint GENERATED ALWAYS AS IDENTITY,
    room_id           bigint      NOT NULL,
    app_user_id       bigint      NOT NULL,
    booking_series_id bigint,
    start_time        timestamptz NOT NULL,
    end_time          timestamptz NOT NULL,
    status            varchar(20) NOT NULL DEFAULT 'ACTIVE',
    version           integer     NOT NULL DEFAULT 0,
    CONSTRAINT pk_booking PRIMARY KEY (booking_id),
    CONSTRAINT fk_booking_room FOREIGN KEY (room_id) REFERENCES room (room_id),
    CONSTRAINT fk_booking_app_user FOREIGN KEY (app_user_id) REFERENCES app_user (app_user_id),
    CONSTRAINT fk_booking_booking_series FOREIGN KEY (booking_series_id) REFERENCES booking_series (booking_series_id),
    CONSTRAINT ck_booking_end_time CHECK (start_time < end_time),
    CONSTRAINT ck_booking_status CHECK (status IN ('ACTIVE', 'CANCELLED')),
    -- no overlap of active bookings in the same room; tstzrange is [) → back-to-back bookings allowed
    CONSTRAINT ex_booking_room_id EXCLUDE USING gist (room_id WITH =, tstzrange(start_time, end_time) WITH &&)
        WHERE (status <> 'CANCELLED')
);

-- index for bookings by user/period deliberately omitted here: added as a separate migration for the measurement (T10)
CREATE INDEX idx_booking_booking_series_id ON booking (booking_series_id);

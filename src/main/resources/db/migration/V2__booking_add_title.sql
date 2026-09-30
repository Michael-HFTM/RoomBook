-- add new title column to booking, backfill values with room name, set not null constraint
ALTER TABLE booking ADD COLUMN title varchar(200);

UPDATE booking b
    SET title = r.name
    FROM room r
    WHERE b.room_id = r.room_id;

ALTER TABLE booking ALTER COLUMN title SET NOT NULL;
ALTER TABLE booking ADD CONSTRAINT ck_booking_title CHECK (btrim(title) <> '');
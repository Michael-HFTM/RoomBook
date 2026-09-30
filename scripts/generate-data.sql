-- Test data generator (T9): ~150'000 bookings, 200 rooms, 500 users, 2 years.
-- Deterministic: setseed plus explicit ORDER BY before every random() call, so reruns produce identical data.
-- Replaces ALL data in the target database (dev seed included).
-- Run: docker compose exec -T postgres psql -U roombook -d roombook -v ON_ERROR_STOP=1 < scripts/generate-data.sql

\timing on

BEGIN;

TRUNCATE booking, booking_series, room, location, app_user RESTART IDENTITY CASCADE;
SELECT setseed(0.42);

INSERT INTO app_user (username, role)
SELECT 'user' || i, CASE WHEN i <= 5 THEN 'ADMIN' ELSE 'USER' END
FROM generate_series(1, 500) i;

-- hierarchy: 4 branches x 2 buildings x 5 floors = 40 floors
INSERT INTO location (name, type)
SELECT 'Standort ' || i, 'BRANCH'
FROM generate_series(1, 4) i;

INSERT INTO location (parent_location_id, name, type)
SELECT l.location_id, 'Gebäude ' || i, 'BUILDING'
FROM location l,
     generate_series(1, 2) i
WHERE l.type = 'BRANCH'
ORDER BY l.location_id, i;

INSERT INTO location (parent_location_id, name, type)
SELECT l.location_id, i || '. OG', 'FLOOR'
FROM location l,
     generate_series(1, 5) i
WHERE l.type = 'BUILDING'
ORDER BY l.location_id, i;

-- 5 rooms per floor = 200 rooms
INSERT INTO room (location_id, name, capacity)
SELECT location_id, 'Raum ' || i, (ARRAY [2, 4, 6, 8, 12, 20])[1 + floor(random() * 6)]
FROM (SELECT l.location_id, i
      FROM location l,
           generate_series(1, 5) i
      WHERE l.type = 'FLOOR'
      ORDER BY l.location_id, i) x;

-- one hourly slot per room and workday 07-18 (Europe/Zurich); a booking starts at the slot and ends within it,
-- so bookings never overlap. Skew: popularity per room and bookings per user follow power(random(), n).
WITH room_popularity AS MATERIALIZED (SELECT room_id, 0.05 + 0.3 * power(random(), 2) AS p
                                      FROM (SELECT room_id FROM room ORDER BY room_id) x),
     slot AS MATERIALIZED (SELECT rp.room_id, rp.p, (d + h * interval '1 hour') AT TIME ZONE 'Europe/Zurich' AS s
                           FROM room_popularity rp,
                                generate_series(timestamp '2025-01-01', timestamp '2026-12-31', interval '1 day') d,
                                generate_series(7, 17) h
                           WHERE extract(isodow FROM d) < 6
                           ORDER BY rp.room_id, d, h)
INSERT INTO booking (room_id, app_user_id, start_time, end_time, title, status)
SELECT room_id,
       1 + floor(power(random(), 2) * 500),
       s,
       s + (30 + 15 * floor(random() * 3)) * interval '1 minute', -- 30/45/60 min
       'Meeting',
       CASE WHEN random() < 0.1 THEN 'CANCELLED' ELSE 'ACTIVE' END
FROM slot
WHERE random() < p;

COMMIT;

ANALYZE;

SELECT 'app_user' AS table_name, count(*) FROM app_user
UNION ALL SELECT 'location', count(*) FROM location
UNION ALL SELECT 'room', count(*) FROM room
UNION ALL SELECT 'booking', count(*) FROM booking
UNION ALL SELECT 'booking cancelled', count(*) FROM booking WHERE status = 'CANCELLED';

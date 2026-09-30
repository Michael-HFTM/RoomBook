-- Dev seed data, only loaded with profile "dev" (application-dev.yaml), never in tests.
-- Repeatable migration: re-runs when this file changes, therefore every insert is idempotent.

INSERT INTO app_user (username, role)
VALUES ('admin', 'ADMIN'),
       ('alice', 'USER'),
       ('bob', 'USER')
ON CONFLICT (username) DO NOTHING;

-- location has no unique name, so existence is checked per node
INSERT INTO location (parent_location_id, name, type)
SELECT NULL, 'Bern', 'BRANCH'
WHERE NOT EXISTS (SELECT 1 FROM location WHERE name = 'Bern' AND parent_location_id IS NULL);

INSERT INTO location (parent_location_id, name, type)
SELECT p.location_id, 'Hauptgebäude', 'BUILDING'
FROM location p
WHERE p.name = 'Bern'
  AND p.parent_location_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM location WHERE name = 'Hauptgebäude' AND parent_location_id = p.location_id);

INSERT INTO location (parent_location_id, name, type)
SELECT p.location_id, f.name, 'FLOOR'
FROM location p
         CROSS JOIN (VALUES ('EG'), ('1. OG')) AS f (name)
WHERE p.name = 'Hauptgebäude'
  AND NOT EXISTS (SELECT 1 FROM location WHERE name = f.name AND parent_location_id = p.location_id);

INSERT INTO room (location_id, name, capacity)
SELECT l.location_id, r.name, r.capacity
FROM location l
         JOIN (VALUES ('EG', 'Sitzungszimmer Aare', 8),
                      ('EG', 'Fokusraum 1', 2),
                      ('1. OG', 'Schulungsraum', 20)) AS r (floor, name, capacity) ON r.floor = l.name
ON CONFLICT (location_id, name) DO NOTHING;

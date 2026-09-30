-- Bookings that count for reports (A7, T6): everything except cancelled bookings, with room and location.
-- Keeps the rule "what counts as booked" in one place instead of repeating the status filter in every report.
CREATE VIEW v_active_booking AS
SELECT b.booking_id,
       b.room_id,
       r.location_id,
       b.app_user_id,
       b.start_time,
       b.end_time
FROM booking b
         JOIN room r ON r.room_id = b.room_id
WHERE b.status <> 'CANCELLED';

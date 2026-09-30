-- T10: A6 booking list filtered by user and period, sorted by start_time.
-- app_user_id first (equality), start_time second (range filter and sort order of the list).
CREATE INDEX idx_booking_app_user_id_start_time ON booking (app_user_id, start_time);

-- T10 measurement: A6 booking list filtered by user and period (BookingRepository#search).
-- SQL copied from the Hibernate log (list + count query of the Page), parameters bound like the application does.
-- Run: docker compose exec -T postgres psql -U roombook -d roombook -v user_id=1 < scripts/measure-booking-search.sql
-- user_id=1 books most (7'810), user_id=496 least (148) with the data from generate-data.sql.

\set ON_ERROR_STOP 1
\if :{?user_id}
\else
\set user_id 1
\endif
\pset pager off

SELECT version();
SELECT indexname FROM pg_indexes WHERE tablename = 'booking' ORDER BY indexname;

-- $1 room_id, $2 app_user_id, $3 all_locations, $4 from, $5 to, $6 status, $7 limit
PREPARE list(bigint, bigint, boolean, timestamptz, timestamptz, varchar, int) AS
    select b1_0.booking_id, r1_0.room_id, r1_0.name, u1_0.app_user_id, u1_0.username,
           b1_0.start_time, b1_0.end_time, b1_0.title, b1_0.status
    from booking b1_0
             join room r1_0 on r1_0.room_id = b1_0.room_id
             join app_user u1_0 on u1_0.app_user_id = b1_0.app_user_id
    where ($1 is null or r1_0.room_id = $1)
      and ($2 is null or u1_0.app_user_id = $2)
      and ($3 = true or 1 = 0)
      and (cast($4 as timestamp(6) with time zone) is null or b1_0.end_time > $4)
      and (cast($5 as timestamp(6) with time zone) is null or b1_0.start_time < $5)
      and ($6 is null or b1_0.status = $6)
    order by b1_0.start_time, b1_0.booking_id
    fetch first $7 rows only;

PREPARE cnt(bigint, bigint, boolean, timestamptz, timestamptz, varchar) AS
    select count(*)
    from booking b1_0
             join room r1_0 on r1_0.room_id = b1_0.room_id
             join app_user u1_0 on u1_0.app_user_id = b1_0.app_user_id
    where ($1 is null or r1_0.room_id = $1)
      and ($2 is null or u1_0.app_user_id = $2)
      and ($3 = true or 1 = 0)
      and (cast($4 as timestamp(6) with time zone) is null or b1_0.end_time > $4)
      and (cast($5 as timestamp(6) with time zone) is null or b1_0.start_time < $5)
      and ($6 is null or b1_0.status = $6);

\set args 'NULL, ' :user_id ', true, ''2026-03-01 00:00+01'', ''2026-04-01 00:00+02'', NULL'

-- runs the statement n times (after one warm-up run) and returns execution times in ms
CREATE FUNCTION pg_temp.bench(stmt text, n int)
    RETURNS TABLE (runs int, min_ms numeric, median_ms numeric, max_ms numeric)
    LANGUAGE plpgsql AS
$$
DECLARE
    plan  json;
    times numeric[] := '{}';
BEGIN
    EXECUTE 'EXPLAIN (ANALYZE, FORMAT JSON) ' || stmt INTO plan;
    FOR i IN 1..n LOOP
        EXECUTE 'EXPLAIN (ANALYZE, FORMAT JSON) ' || stmt INTO plan;
        times := times || (plan -> 0 ->> 'Execution Time')::numeric;
    END LOOP;
    RETURN QUERY
        SELECT n,
               round(min(t), 3),
               round(percentile_cont(0.5) WITHIN GROUP (ORDER BY t)::numeric, 3),
               round(max(t), 3)
        FROM unnest(times) t;
END;
$$;

\echo '=== custom plan (parameters known at planning) ==='
SET plan_cache_mode = force_custom_plan;
EXPLAIN (ANALYZE, BUFFERS) EXECUTE list(:args, 20);
EXPLAIN (ANALYZE, BUFFERS) EXECUTE cnt(:args);
SELECT 'list' AS query, * FROM pg_temp.bench(format('EXECUTE list(%s, 20)', :'args'), 10)
UNION ALL
SELECT 'count', * FROM pg_temp.bench(format('EXECUTE cnt(%s)', :'args'), 10);

\echo '=== generic plan (parameters unknown at planning) ==='
SET plan_cache_mode = force_generic_plan;
EXPLAIN (ANALYZE, BUFFERS) EXECUTE list(:args, 20);
EXPLAIN (ANALYZE, BUFFERS) EXECUTE cnt(:args);
SELECT 'list' AS query, * FROM pg_temp.bench(format('EXECUTE list(%s, 20)', :'args'), 10)
UNION ALL
SELECT 'count', * FROM pg_temp.bench(format('EXECUTE cnt(%s)', :'args'), 10);

\echo '=== auto (default): which plan does PostgreSQL choose after repeated executions? ==='
RESET plan_cache_mode;
DEALLOCATE ALL;
PREPARE list(bigint, bigint, boolean, timestamptz, timestamptz, varchar, int) AS
    select b1_0.booking_id, r1_0.room_id, r1_0.name, u1_0.app_user_id, u1_0.username,
           b1_0.start_time, b1_0.end_time, b1_0.title, b1_0.status
    from booking b1_0
             join room r1_0 on r1_0.room_id = b1_0.room_id
             join app_user u1_0 on u1_0.app_user_id = b1_0.app_user_id
    where ($1 is null or r1_0.room_id = $1)
      and ($2 is null or u1_0.app_user_id = $2)
      and ($3 = true or 1 = 0)
      and (cast($4 as timestamp(6) with time zone) is null or b1_0.end_time > $4)
      and (cast($5 as timestamp(6) with time zone) is null or b1_0.start_time < $5)
      and ($6 is null or b1_0.status = $6)
    order by b1_0.start_time, b1_0.booking_id
    fetch first $7 rows only;
SELECT * FROM pg_temp.bench(format('EXECUTE list(%s, 20)', :'args'), 10);
SELECT name, generic_plans, custom_plans FROM pg_prepared_statements;

package ch.diamondh3art.roombook.report;

import ch.diamondh3art.roombook.location.LocationService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

// A7 reports as plain SQL via JdbcClient: set-based range arithmetic and aggregation are simpler in SQL than in JPQL
@Service
@Transactional(readOnly = true)
public class ReportService {

    /*
     * Occupancy per room (A7, T6). Business hours are Mon–Fri 07:00–18:00 local time in Europe/Zurich; each window
     * is built in local time and converted to timestamptz, so DST changes are handled. A booking counts with its
     * intersection (*) with each window, which caps it to business hours. Booked hours are summed per room before
     * joining the rooms, so several bookings or windows per room cannot multiply the available hours.
     * All rooms of the location subtree count, including deactivated ones (no deactivation date in the schema).
     */
    private static final String OCCUPANCY_SQL = """
            WITH RECURSIVE scope (location_id) AS (
                SELECT location_id FROM location WHERE location_id = :locationId
                UNION ALL
                SELECT l.location_id
                FROM location l
                         JOIN scope s ON l.parent_location_id = s.location_id),
            business_window (slot) AS (
                SELECT tstzrange((CAST(d AS date) + TIME '07:00') AT TIME ZONE 'Europe/Zurich',
                                 (CAST(d AS date) + TIME '18:00') AT TIME ZONE 'Europe/Zurich')
                FROM generate_series(CAST(:from AS date), CAST(:to AS date), INTERVAL '1 day') AS g (d)
                WHERE EXTRACT(ISODOW FROM d) < 6),
            available (hours) AS (
                SELECT COALESCE(SUM(EXTRACT(EPOCH FROM upper(slot) - lower(slot))), 0) / 3600
                FROM business_window),
            booked (room_id, hours) AS (
                SELECT v.room_id,
                       SUM(EXTRACT(EPOCH FROM upper(w.slot * tstzrange(v.start_time, v.end_time))
                                             - lower(w.slot * tstzrange(v.start_time, v.end_time)))) / 3600
                FROM v_active_booking v
                         JOIN business_window w ON w.slot && tstzrange(v.start_time, v.end_time)
                GROUP BY v.room_id)
            SELECT r.room_id,
                   r.name,
                   r.active,
                   ROUND(COALESCE(b.hours, 0), 2)                              AS booked_hours,
                   ROUND(a.hours, 2)                                           AS available_hours,
                   ROUND(COALESCE(b.hours, 0) / NULLIF(a.hours, 0), 4)         AS rate
            FROM room r
                     CROSS JOIN available a
                     LEFT JOIN booked b ON b.room_id = r.room_id
            WHERE CAST(:locationId AS bigint) IS NULL
               OR r.location_id IN (SELECT location_id FROM scope)
            ORDER BY rate DESC NULLS LAST, r.name, r.room_id""";

    private final JdbcClient jdbcClient;
    private final LocationService locationService;

    public ReportService(JdbcClient jdbcClient, LocationService locationService) {
        this.jdbcClient = jdbcClient;
        this.locationService = locationService;
    }

    // from and to are local dates (Europe/Zurich), both inclusive
    public OccupancyReport occupancy(LocalDate from, LocalDate to, Long locationId) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
        if (locationId != null) {
            locationService.getLocation(locationId);
        }
        List<RoomOccupancy> rooms = jdbcClient.sql(OCCUPANCY_SQL)
                .param("locationId", locationId)
                .param("from", from)
                .param("to", to)
                .query((rs, row) -> new RoomOccupancy(rs.getLong("room_id"), rs.getString("name"),
                        rs.getBoolean("active"), rs.getBigDecimal("booked_hours"),
                        rs.getBigDecimal("available_hours"), rs.getBigDecimal("rate")))
                .list();

        // rate of the location = all booked hours / all available hours of its rooms
        BigDecimal booked = rooms.stream().map(RoomOccupancy::bookedHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal available = rooms.stream().map(RoomOccupancy::availableHours).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rate = available.signum() == 0 ? null : booked.divide(available, 4, RoundingMode.HALF_UP);
        return new OccupancyReport(from, to, locationId, booked, available, rate, rooms);
    }
}

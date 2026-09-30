package ch.diamondh3art.roombook;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the business rules of V1 are enforced by the database itself (T1, T2),
 * independent of the API. Each test runs in a transaction that is rolled back afterwards.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchemaConstraintsTest {

    private static final OffsetDateTime DAY = OffsetDateTime.parse("2026-10-05T00:00:00+02:00");

    @Autowired
    JdbcClient jdbc;

    long locationId;
    long roomId;
    long userId;

    @BeforeEach
    void setUp() {
        locationId = insertLocation("Test-Building", null);
        roomId = insertRoom(locationId, "Room 1", 10);
        userId = jdbc.sql("INSERT INTO app_user (username, role) VALUES ('tester', 'USER') RETURNING app_user_id")
                .query(Long.class).single();
    }

    // --- booking: exclusion constraint (A5) ---

    @Test
    void overlappingBookingInSameRoomIsRejected() {
        insertBooking(roomId, 10, 12, "ACTIVE");

        assertViolates("23P01", "ex_booking_room_id", () -> insertBooking(roomId, 11, 13, "ACTIVE"));
    }

    @Test
    void backToBackBookingsAreAllowed() {
        insertBooking(roomId, 10, 11, "ACTIVE");
        insertBooking(roomId, 11, 12, "ACTIVE");

        assertThat(countBookings()).isEqualTo(2);
    }

    @Test
    void cancelledBookingDoesNotBlockSlot() {
        insertBooking(roomId, 10, 12, "CANCELLED");
        insertBooking(roomId, 10, 12, "ACTIVE");

        assertThat(countBookings()).isEqualTo(2);
    }

    @Test
    void sameSlotInOtherRoomIsAllowed() {
        long otherRoomId = insertRoom(locationId, "Room 2", 10);
        insertBooking(roomId, 10, 12, "ACTIVE");
        insertBooking(otherRoomId, 10, 12, "ACTIVE");

        assertThat(countBookings()).isEqualTo(2);
    }

    // --- CHECK and UNIQUE constraints ---

    @Test
    void bookingMustEndAfterStart() {
        assertViolates("23514", "ck_booking_end_time", () -> insertBooking(roomId, 12, 12, "ACTIVE"));
    }

    @Test
    void roomCapacityMustBePositive() {
        assertViolates("23514", "ck_room_capacity", () -> insertRoom(locationId, "Room 0", 0));
    }

    @Test
    void roomNameMustBeUniquePerLocation() {
        assertViolates("23505", "uq_room_location_id_name", () -> insertRoom(locationId, "Room 1", 5));
    }

    @Test
    void seriesIsLimitedToTwelveOccurrences() {
        assertViolates("23514", "ck_booking_series_occurrences", () -> jdbc.sql("""
                        INSERT INTO booking_series (app_user_id, rule, occurrences) VALUES (?, 'WEEKLY', 13)""")
                .params(userId).update());
    }

    // --- location hierarchy: cycle trigger (A1) ---

    @Test
    void movingLocationBelowOwnDescendantIsRejected() {
        long floorId = insertLocation("Floor 1", locationId);
        long subId = insertLocation("Wing A", floorId);

        assertViolates("23514", "ck_location_no_cycle", () -> moveLocation(locationId, subId));
    }

    @Test
    void movingLocationToOtherBranchIsAllowed() {
        long otherId = insertLocation("Other-Building", null);
        long floorId = insertLocation("Floor 1", locationId);

        moveLocation(floorId, otherId);

        assertThat(jdbc.sql("SELECT parent_location_id FROM location WHERE location_id = ?")
                .params(floorId).query(Long.class).single()).isEqualTo(otherId);
    }

    // --- helpers ---

    private static void assertViolates(String sqlState, String constraint, ThrowingCallable call) {
        assertThatThrownBy(call).rootCause().isInstanceOfSatisfying(PSQLException.class, e -> {
            assertThat(e.getSQLState()).isEqualTo(sqlState);
            assertThat(e.getServerErrorMessage().getConstraint()).isEqualTo(constraint);
        });
    }

    private long insertLocation(String name, Long parentId) {
        return jdbc.sql("""
                        INSERT INTO location (parent_location_id, name, type) VALUES (?, ?, 'BUILDING')
                        RETURNING location_id""")
                .params(parentId, name).query(Long.class).single();
    }

    private long insertRoom(long locationId, String name, int capacity) {
        return jdbc.sql("INSERT INTO room (location_id, name, capacity) VALUES (?, ?, ?) RETURNING room_id")
                .params(locationId, name, capacity).query(Long.class).single();
    }

    private void insertBooking(long roomId, int fromHour, int toHour, String status) {
        jdbc.sql("""
                        INSERT INTO booking (room_id, app_user_id, start_time, end_time, status)
                        VALUES (?, ?, ?, ?, ?)""")
                .params(roomId, userId, DAY.plusHours(fromHour), DAY.plusHours(toHour), status)
                .update();
    }

    private void moveLocation(long locationId, long newParentId) {
        jdbc.sql("UPDATE location SET parent_location_id = ? WHERE location_id = ?")
                .params(newParentId, locationId).update();
    }

    private long countBookings() {
        return jdbc.sql("SELECT count(*) FROM booking").query(Long.class).single();
    }
}

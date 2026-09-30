package ch.diamondh3art.roombook.report;

import ch.diamondh3art.roombook.TestcontainersConfiguration;
import ch.diamondh3art.roombook.booking.Booking;
import ch.diamondh3art.roombook.booking.BookingRepository;
import ch.diamondh3art.roombook.common.user.AppUser;
import ch.diamondh3art.roombook.common.user.AppUserRepository;
import ch.diamondh3art.roombook.common.user.Role;
import ch.diamondh3art.roombook.location.Location;
import ch.diamondh3art.roombook.location.LocationRepository;
import ch.diamondh3art.roombook.location.LocationType;
import ch.diamondh3art.roombook.room.Room;
import ch.diamondh3art.roombook.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A7 and T6 via REST with hand-calculated results. Week Mon 2030-01-07 to Sun 2030-01-13 (winter time, +01:00):
 * 5 business days × 11 h = 55 h available per room.
 * <pre>
 * Room A (Bern/Floor): Mon 06–08 → 1 h, Mon 17–20 → 1 h, Tue 09–12 → 3 h, Wed 09–12 cancelled → 0 h,
 *                      Fri 17:00 – next Mon 08:00 → 1 h (weekend and next week outside) = 6 h  → 6/55  = 0.1091
 * Room B (Bern/Floor): Sat 10–12 → 0 h, Wed 07–18 → 11 h                               = 11 h → 11/55 = 0.2000
 * Room C (Bern/Floor): no bookings, deactivated                                          = 0 h  → 0.0000
 * Room Z (Zurich):     Thu 08–10 → 2 h                                                   = 2 h  → 2/55  = 0.0364
 * Bern: 17 h / 165 h = 0.1030; all locations: 19 h / 220 h = 0.0864
 * </pre>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    LocationRepository locationRepository;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    BookingRepository bookingRepository;

    AppUser user;
    Location bern;
    Room roomA;

    @BeforeEach
    void setUp() {
        user = appUserRepository.save(new AppUser("reporter", Role.USER));
        bern = locationRepository.save(new Location(null, "Bern", LocationType.BRANCH));
        Location floor = locationRepository.save(new Location(bern, "Floor 1", LocationType.FLOOR));
        Location zurich = locationRepository.save(new Location(null, "Zurich", LocationType.BRANCH));
        roomA = roomRepository.save(new Room(floor, "Room A", 8));
        Room roomB = roomRepository.save(new Room(floor, "Room B", 8));
        roomRepository.save(new Room(floor, "Room C", 8)).deactivate();
        Room roomZ = roomRepository.save(new Room(zurich, "Room Z", 8));

        book(roomA, "2030-01-07T06:00", "2030-01-07T08:00");
        book(roomA, "2030-01-07T17:00", "2030-01-07T20:00");
        book(roomA, "2030-01-08T09:00", "2030-01-08T12:00");
        book(roomA, "2030-01-09T09:00", "2030-01-09T12:00").cancel();
        book(roomA, "2030-01-11T17:00", "2030-01-14T08:00");
        book(roomB, "2030-01-12T10:00", "2030-01-12T12:00");
        book(roomB, "2030-01-09T07:00", "2030-01-09T18:00");
        book(roomZ, "2030-01-10T08:00", "2030-01-10T10:00");
        bookingRepository.flush();
    }

    @Test
    void occupancyPerRoomIsCappedToBusinessHoursAndRanked() {
        MvcTestResult result = occupancy("from=2030-01-07&to=2030-01-13&locationId=" + bern.getId());

        assertThat(result).bodyJson().extractingPath("$.rooms[*].roomName").isEqualTo(List.of("Room B", "Room A", "Room C"));
        assertThat(result).bodyJson().extractingPath("$.rooms[1].bookedHours").isEqualTo(6.0);
        assertThat(result).bodyJson().extractingPath("$.rooms[1].availableHours").isEqualTo(55.0);
        assertThat(result).bodyJson().extractingPath("$.rooms[1].rate").isEqualTo(0.1091);
        assertThat(result).bodyJson().extractingPath("$.rooms[0].rate").isEqualTo(0.2);
        assertThat(result).bodyJson().extractingPath("$.rooms[2].bookedHours").isEqualTo(0.0);
        assertThat(result).bodyJson().extractingPath("$.rooms[2].active").isEqualTo(false);
    }

    @Test
    void occupancyOfLocationSumsAllRoomsOfSubtree() {
        MvcTestResult result = occupancy("from=2030-01-07&to=2030-01-13&locationId=" + bern.getId());

        assertThat(result).bodyJson().extractingPath("$.bookedHours").isEqualTo(17.0);
        assertThat(result).bodyJson().extractingPath("$.availableHours").isEqualTo(165.0);
        assertThat(result).bodyJson().extractingPath("$.rate").isEqualTo(0.103);
    }

    @Test
    void occupancyWithoutLocationCoversAllRooms() {
        MvcTestResult result = occupancy("from=2030-01-07&to=2030-01-13");

        assertThat(result).bodyJson().extractingPath("$.rooms.length()").isEqualTo(4);
        assertThat(result).bodyJson().extractingPath("$.bookedHours").isEqualTo(19.0);
        assertThat(result).bodyJson().extractingPath("$.rate").isEqualTo(0.0864);
    }

    @Test
    void businessHoursFollowLocalTimeAcrossDstChange() {
        // Fri 2030-10-25 (+02:00) and Mon 2030-10-28 (+01:00), both fully booked 07:00–18:00 local time
        book(roomA, "2030-10-25T07:00+02:00", "2030-10-25T18:00+02:00");
        book(roomA, "2030-10-28T07:00+01:00", "2030-10-28T18:00+01:00");
        bookingRepository.flush();

        MvcTestResult result = occupancy("from=2030-10-25&to=2030-10-28&locationId=" + bern.getId());

        assertThat(result).bodyJson().extractingPath("$.rooms[0].roomName").isEqualTo("Room A");
        assertThat(result).bodyJson().extractingPath("$.rooms[0].bookedHours").isEqualTo(22.0);
        assertThat(result).bodyJson().extractingPath("$.rooms[0].rate").isEqualTo(1.0);
    }

    @Test
    void periodWithoutBusinessDaysHasNoRate() {
        MvcTestResult result = occupancy("from=2030-01-12&to=2030-01-13");

        assertThat(result).bodyJson().extractingPath("$.availableHours").isEqualTo(0.0);
        assertThat(result).bodyJson().extractingPath("$.rate").isNull();
    }

    @Test
    void fromAfterToIsBadRequest() {
        assertThat(mvc.get().uri("/api/reports/occupancy?from=2030-01-13&to=2030-01-07"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownLocationIsNotFound() {
        assertThat(mvc.get().uri("/api/reports/occupancy?from=2030-01-07&to=2030-01-13&locationId=-1"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    // times without offset are winter time in Zurich (+01:00)
    private Booking book(Room room, String start, String end) {
        return bookingRepository.save(new Booking(room, user, null, parse(start), parse(end), "Meeting"));
    }

    private static OffsetDateTime parse(String time) {
        return OffsetDateTime.parse(time.contains("+") ? time : time + "+01:00");
    }

    private MvcTestResult occupancy(String query) {
        MvcTestResult result = mvc.get().uri("/api/reports/occupancy?" + query).exchange();
        assertThat(result).hasStatusOk();
        return result;
    }
}

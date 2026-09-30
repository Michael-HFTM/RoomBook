package ch.diamondh3art.roombook.booking;

import ch.diamondh3art.roombook.TestcontainersConfiguration;
import ch.diamondh3art.roombook.common.user.AppUser;
import ch.diamondh3art.roombook.common.user.AppUserRepository;
import ch.diamondh3art.roombook.common.user.Role;
import ch.diamondh3art.roombook.location.Location;
import ch.diamondh3art.roombook.location.LocationRepository;
import ch.diamondh3art.roombook.location.LocationType;
import ch.diamondh3art.roombook.room.Room;
import ch.diamondh3art.roombook.room.RoomRepository;
import com.jayway.jsonpath.JsonPath;
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
 * A6 and T7 via REST: DB-side filters, stable order (start time, id) and paging.
 * Data: Bern → Floor 1 (room1), Floor 2 (room2); Zurich (room3). Bookings in start order:
 * b1 room1/alice Mon 09:00, b2 room2/bob Mon 09:00 (same start), b3 room3/alice Tue CANCELLED, b4 room1/bob Wed.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingSearchTest {

    private static final OffsetDateTime MONDAY = OffsetDateTime.parse("2030-01-07T09:00:00Z");

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

    AppUser alice;
    Location bern;
    Room room1;
    long b1;
    long b2;
    long b3;
    long b4;

    @BeforeEach
    void setUp() {
        alice = appUserRepository.save(new AppUser("alice", Role.USER));
        AppUser bob = appUserRepository.save(new AppUser("bob", Role.USER));
        bern = locationRepository.save(new Location(null, "Bern", LocationType.BRANCH));
        room1 = roomRepository.save(new Room(locationRepository.save(new Location(bern, "Floor 1", LocationType.FLOOR)), "Room 1", 8));
        Room room2 = roomRepository.save(new Room(locationRepository.save(new Location(bern, "Floor 2", LocationType.FLOOR)), "Room 2", 8));
        Room room3 = roomRepository.save(new Room(locationRepository.save(new Location(null, "Zurich", LocationType.BRANCH)), "Room 3", 8));
        b1 = book(room1, alice, MONDAY);
        b2 = book(room2, bob, MONDAY);
        b3 = book(room3, alice, MONDAY.plusDays(1));
        bookingRepository.findById(b3).orElseThrow().cancel();
        b4 = book(room1, bob, MONDAY.plusDays(2));
        bookingRepository.flush();
    }

    @Test
    void noFilterReturnsAllInStableOrder() {
        MvcTestResult result = search("");

        // b1 and b2 start at the same time, the id decides
        assertThat(ids(result)).containsExactly(b1, b2, b3, b4);
        assertThat(result).bodyJson().extractingPath("$.page.totalElements").isEqualTo(4);
    }

    @Test
    void pagesDoNotOverlapAndKeepOrder() {
        MvcTestResult first = search("size=3&page=0");
        MvcTestResult second = search("size=3&page=1");

        assertThat(ids(first)).containsExactly(b1, b2, b3);
        assertThat(ids(second)).containsExactly(b4);
        assertThat(second).bodyJson().extractingPath("$.page.totalPages").isEqualTo(2);
    }

    @Test
    void filterByUser() {
        assertThat(ids(search("userId=" + alice.getId()))).containsExactly(b1, b3);
    }

    @Test
    void filterByRoom() {
        assertThat(ids(search("roomId=" + room1.getId()))).containsExactly(b1, b4);
    }

    @Test
    void filterByLocationIncludesSubLevels() {
        assertThat(ids(search("locationId=" + bern.getId()))).containsExactly(b1, b2, b4);
    }

    @Test
    void filterByStatus() {
        assertThat(ids(search("status=CANCELLED"))).containsExactly(b3);
    }

    @Test
    void filterByPeriodReturnsOverlappingBookings() {
        // b1/b2 end exactly at 'from' (10:00) and are excluded, b4 starts after 'to'
        assertThat(ids(search("from=2030-01-07T10:00:00Z&to=2030-01-09T00:00:00Z"))).containsExactly(b3);
        assertThat(ids(search("from=2030-01-07T09:30:00Z&to=2030-01-07T09:45:00Z"))).containsExactly(b1, b2);
    }

    @Test
    void filtersAreCombined() {
        assertThat(ids(search("userId=" + alice.getId() + "&status=ACTIVE&locationId=" + bern.getId())))
                .containsExactly(b1);
    }

    @Test
    void pageSizeIsLimited() {
        assertThat(mvc.get().uri("/api/bookings?size=101")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownLocationIsNotFound() {
        assertThat(mvc.get().uri("/api/bookings?locationId=-1")).hasStatus(HttpStatus.NOT_FOUND);
    }

    private long book(Room room, AppUser user, OffsetDateTime start) {
        return bookingRepository.save(new Booking(room, user, null, start, start.plusHours(1), "Meeting")).getId();
    }

    private MvcTestResult search(String query) {
        MvcTestResult result = mvc.get().uri("/api/bookings?" + query).exchange();
        assertThat(result).hasStatusOk();
        return result;
    }

    private static List<Long> ids(MvcTestResult result) {
        List<Number> ids = JsonPath.read(new String(result.getResponse().getContentAsByteArray()), "$.content[*].id");
        return ids.stream().map(Number::longValue).toList();
    }
}

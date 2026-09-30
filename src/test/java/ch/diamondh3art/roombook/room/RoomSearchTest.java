package ch.diamondh3art.roombook.room;

import ch.diamondh3art.roombook.TestcontainersConfiguration;
import ch.diamondh3art.roombook.booking.Booking;
import ch.diamondh3art.roombook.booking.BookingRepository;
import ch.diamondh3art.roombook.common.user.AppUser;
import ch.diamondh3art.roombook.common.user.AppUserRepository;
import ch.diamondh3art.roombook.common.user.Role;
import ch.diamondh3art.roombook.location.Location;
import ch.diamondh3art.roombook.location.LocationRepository;
import ch.diamondh3art.roombook.location.LocationType;
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
 * A3 via REST: free rooms by period, minimum capacity and location subtree.
 * Hierarchy: Bern (active) → Floor 1 with Small (2), Big (10, booked 10–11), Closed (10, deactivated);
 * Zurich (active) → Zurich Room (10); Basel (deactivated) → Floor B → Hidden (10).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomSearchTest {

    private static final OffsetDateTime TEN = OffsetDateTime.parse("2030-01-07T10:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    LocationRepository locationRepository;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    AppUserRepository appUserRepository;

    Location bern;
    Location floor;
    Room zurichRoom;
    AppUser user;

    @BeforeEach
    void setUp() {
        user = appUserRepository.save(new AppUser("searcher", Role.USER));
        bern = locationRepository.save(new Location(null, "Bern", LocationType.BRANCH));
        floor = locationRepository.save(new Location(bern, "Floor 1", LocationType.FLOOR));
        roomRepository.save(new Room(floor, "Small", 2));
        Room big = roomRepository.save(new Room(floor, "Big", 10));
        roomRepository.save(new Room(floor, "Closed", 10)).deactivate();
        Location zurich = locationRepository.save(new Location(null, "Zurich", LocationType.BRANCH));
        zurichRoom = roomRepository.save(new Room(zurich, "Zurich Room", 10));
        Location basel = locationRepository.save(new Location(null, "Basel", LocationType.BRANCH));
        Location baselFloor = locationRepository.save(new Location(basel, "Floor B", LocationType.FLOOR));
        roomRepository.save(new Room(baselFloor, "Hidden", 10));
        basel.deactivate();
        bookingRepository.save(new Booking(big, user, null, TEN, TEN.plusHours(1), "Busy"));
        bookingRepository.flush();
    }

    @Test
    void bookedRoomIsNotFree() {
        assertThat(names(search(TEN, TEN.plusHours(1), 5, null))).containsExactly("Zurich Room");
    }

    @Test
    void partialOverlapAlsoBlocks() {
        assertThat(names(search(TEN.plusMinutes(59), TEN.plusHours(2), 5, null))).containsExactly("Zurich Room");
    }

    @Test
    void backToBackSlotIsFree() {
        assertThat(names(search(TEN.plusHours(1), TEN.plusHours(2), 5, null))).containsExactly("Big", "Zurich Room");
    }

    @Test
    void cancelledBookingDoesNotBlock() {
        bookingRepository.save(new Booking(zurichRoom, user, null, TEN, TEN.plusHours(1), "Cancelled")).cancel();
        bookingRepository.flush();

        assertThat(names(search(TEN, TEN.plusHours(1), 5, null))).containsExactly("Zurich Room");
    }

    @Test
    void locationFilterIncludesSubLevelsOnly() {
        MvcTestResult result = search(TEN.plusHours(1), TEN.plusHours(2), 1, bern.getId());

        // sorted by capacity, then name; Closed (deactivated) and rooms of other branches are excluded
        assertThat(names(result)).containsExactly("Small", "Big");
    }

    @Test
    void roomsBelowDeactivatedLocationAreNotFree() {
        bern.deactivate();
        locationRepository.flush();

        assertThat(names(search(TEN.plusHours(1), TEN.plusHours(2), 1, null))).containsExactly("Zurich Room");
        assertThat(names(search(TEN.plusHours(1), TEN.plusHours(2), 1, floor.getId()))).isEmpty();
    }

    @Test
    void emptyPeriodIsBadRequest() {
        assertThat(search(TEN, TEN, 1, null)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownLocationIsNotFound() {
        assertThat(search(TEN, TEN.plusHours(1), 1, -1L)).hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult search(OffsetDateTime from, OffsetDateTime to, int minCapacity, Long locationId) {
        var request = mvc.get().uri("/api/rooms/free")
                .param("from", from.toString())
                .param("to", to.toString())
                .param("minCapacity", String.valueOf(minCapacity));
        if (locationId != null) {
            request = request.param("locationId", locationId.toString());
        }
        return request.exchange();
    }

    private static List<String> names(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray()), "$[*].name");
    }
}

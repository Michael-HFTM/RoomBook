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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A4 and A5 via REST: single and weekly bookings, business rules, permissions (403) and conflicts (409).
 * Each test runs in a transaction that is rolled back afterwards.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingApiTest {

    // Monday; DST in Europe/Zurich ends on Sunday 2030-10-27
    private static final String START = "2030-10-21T10:00:00+02:00";
    private static final String END = "2030-10-21T11:00:00+02:00";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    LocationRepository locationRepository;

    @Autowired
    RoomRepository roomRepository;

    long adminId;
    long userId;
    long otherUserId;
    Room room;

    @BeforeEach
    void setUp() {
        adminId = appUserRepository.save(new AppUser("admin", Role.ADMIN)).getId();
        userId = appUserRepository.save(new AppUser("user", Role.USER)).getId();
        otherUserId = appUserRepository.save(new AppUser("other", Role.USER)).getId();
        Location floor = locationRepository.save(new Location(null, "Floor 1", LocationType.FLOOR));
        room = roomRepository.save(new Room(floor, "Room 1", 8));
    }

    @Test
    void userBooksRoomOnce() {
        MvcTestResult result = post(userId, body(START, END, null));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$[0].seriesId").isNull();
        assertThat(result).bodyJson().extractingPath("$[0].status").isEqualTo("ACTIVE");
    }

    @Test
    void weeklySeriesKeepsLocalTimeAcrossDstChange() {
        MvcTestResult result = post(userId, body(START, END, 3));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$[2].seriesId").isNotNull();
        // week 2 is after the switch to winter time: still 10:00 local time, offset +01:00
        OffsetDateTime second = OffsetDateTime.parse(read(result, "$[1].startTime"));
        assertThat(second.atZoneSameInstant(ZoneId.of("Europe/Zurich")).toLocalTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(second.toInstant()).isEqualTo(OffsetDateTime.parse("2030-10-28T09:00:00Z").toInstant());
    }

    @Test
    void startAndEndAreTruncatedToMinutes() {
        MvcTestResult result = post(userId, body("2030-10-21T10:00:45.123+02:00", "2030-10-21T11:00:59+02:00", null));

        assertThat(OffsetDateTime.parse(read(result, "$[0].startTime")).toInstant())
                .isEqualTo(OffsetDateTime.parse(START).toInstant());
        assertThat(OffsetDateTime.parse(read(result, "$[0].endTime")).toInstant())
                .isEqualTo(OffsetDateTime.parse(END).toInstant());
    }

    @Test
    void seriesWithMoreThanTwelveOccurrencesIsBadRequest() {
        assertThat(post(userId, body(START, END, 13))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void startInPastIsBadRequest() {
        String start = OffsetDateTime.now().minusDays(1).toString();
        String end = OffsetDateTime.now().plusDays(1).toString();

        assertThat(post(userId, body(start, end, null))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void startMustBeBeforeEnd() {
        assertThat(post(userId, body(END, START, null))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deactivatedRoomIsNotBookable() {
        room.deactivate();

        assertThat(post(userId, body(START, END, null))).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void roomBelowDeactivatedLocationIsNotBookable() {
        Location branch = locationRepository.save(new Location(null, "Branch", LocationType.BRANCH));
        Location floor = locationRepository.save(new Location(branch, "Floor 2", LocationType.FLOOR));
        room = roomRepository.save(new Room(floor, "Room 2", 8));
        branch.deactivate();
        locationRepository.flush();

        assertThat(post(userId, body(START, END, null))).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void overlappingBookingIsConflict() {
        post(userId, body(START, END, null));

        MvcTestResult result = post(otherUserId, body("2030-10-21T10:30:00+02:00", "2030-10-21T11:30:00+02:00", null));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.constraint").isEqualTo("ex_booking_room_id");
    }

    @Test
    void missingHeaderIsUnauthorized() {
        assertThat(mvc.post().uri("/api/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(body(START, END, null))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void bookerChangesBooking() {
        long id = create();

        MvcTestResult result = put(userId, id, update("2030-10-21T12:00:00+02:00", "2030-10-21T13:00:00+02:00", 0));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Moved");
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(1);
    }

    @Test
    void adminChangesBookingOfOtherUser() {
        long id = create();

        assertThat(put(adminId, id, update(START, END, 0))).hasStatus(HttpStatus.OK);
    }

    @Test
    void otherUserMayNotChangeOrCancel() {
        long id = create();

        assertThat(put(otherUserId, id, update(START, END, 0))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete(otherUserId, id)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staleVersionIsConflict() {
        long id = create();
        put(userId, id, update(START, END, 0));

        assertThat(put(adminId, id, update(START, END, 0))).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void cancelKeepsBookingWithStatusCancelled() {
        long id = create();

        assertThat(delete(userId, id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/bookings/{id}", id))
                .bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        // cancelled booking frees the slot
        assertThat(post(otherUserId, body(START, END, null))).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void cancelledBookingCannotBeChanged() {
        long id = create();
        delete(userId, id);

        assertThat(put(userId, id, update(START, END, 1))).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void unknownBookingIsNotFound() {
        assertThat(mvc.get().uri("/api/bookings/{id}", -1)).hasStatus(HttpStatus.NOT_FOUND);
    }

    private long create() {
        MvcTestResult result = post(userId, body(START, END, null));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.<Number>read(content(result), "$[0].id").longValue();
    }

    private MvcTestResult post(long asUser, String json) {
        return mvc.post().uri("/api/bookings").header("X-User-Id", asUser)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult put(long asUser, long id, String json) {
        return mvc.put().uri("/api/bookings/{id}", id).header("X-User-Id", asUser)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult delete(long asUser, long id) {
        return mvc.delete().uri("/api/bookings/{id}", id).header("X-User-Id", asUser).exchange();
    }

    private String body(String start, String end, Integer occurrences) {
        return """
                {"roomId": %d, "startTime": "%s", "endTime": "%s", "title": "Meeting", "occurrences": %s}"""
                .formatted(room.getId(), start, end, occurrences);
    }

    private static String update(String start, String end, int version) {
        return """
                {"startTime": "%s", "endTime": "%s", "title": "Moved", "version": %d}"""
                .formatted(start, end, version);
    }

    private static String read(MvcTestResult result, String path) {
        return JsonPath.read(content(result), path);
    }

    private static String content(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray());
    }
}

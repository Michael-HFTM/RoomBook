package ch.diamondh3art.roombook.room;

import ch.diamondh3art.roombook.TestcontainersConfiguration;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A2 via REST: admins create, update and deactivate rooms; unique name per location (409), capacity > 0 (400).
 * Each test runs in a transaction that is rolled back afterwards.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    LocationRepository locationRepository;

    long adminId;
    long userId;
    long floorId;

    @BeforeEach
    void setUp() {
        adminId = appUserRepository.save(new AppUser("admin", Role.ADMIN)).getId();
        userId = appUserRepository.save(new AppUser("user", Role.USER)).getId();
        floorId = locationRepository.save(new Location(null, "Floor 1", LocationType.FLOOR)).getId();
    }

    @Test
    void adminCreatesRoom() {
        MvcTestResult result = post(adminId, body(floorId, "Room 1", 8));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.locationId").asNumber().isEqualTo((int) floorId);
        assertThat(result).bodyJson().extractingPath("$.capacity").isEqualTo(8);
        assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
    }

    @Test
    void userWithoutAdminRoleIsForbidden() {
        assertThat(post(userId, body(floorId, "Room 1", 8))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void duplicateNameInSameLocationIsConflict() {
        create("Room 1");

        MvcTestResult result = post(adminId, body(floorId, "Room 1", 4));

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.constraint").isEqualTo("uq_room_location_id_name");
    }

    @Test
    void sameNameInOtherLocationIsAllowed() {
        long otherFloor = locationRepository.save(new Location(null, "Floor 2", LocationType.FLOOR)).getId();
        create("Room 1");

        assertThat(post(adminId, body(otherFloor, "Room 1", 4))).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void capacityMustBePositive() {
        assertThat(post(adminId, body(floorId, "Room 1", 0))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownLocationIsNotFound() {
        assertThat(post(adminId, body(-1L, "Room 1", 8))).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminUpdatesRoom() {
        long room = create("Room 1");

        MvcTestResult result = mvc.put().uri("/api/rooms/{id}", room).header("X-User-Id", adminId)
                .contentType(MediaType.APPLICATION_JSON).content(body(floorId, "Room 1a", 12)).exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Room 1a");
        assertThat(result).bodyJson().extractingPath("$.capacity").isEqualTo(12);
    }

    @Test
    void adminDeactivatesRoom() {
        long room = create("Room 1");

        assertThat(mvc.delete().uri("/api/rooms/{id}", room).header("X-User-Id", adminId))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/rooms/{id}", room))
                .bodyJson().extractingPath("$.active").isEqualTo(false);
    }

    private long create(String name) {
        MvcTestResult result = post(adminId, body(floorId, name, 8));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.<Number>read(new String(result.getResponse().getContentAsByteArray()), "$.id").longValue();
    }

    private MvcTestResult post(long asUser, String json) {
        return mvc.post().uri("/api/rooms").header("X-User-Id", asUser)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String body(Long locationId, String name, int capacity) {
        return """
                {"locationId": %s, "name": "%s", "capacity": %d}""".formatted(locationId, name, capacity);
    }
}

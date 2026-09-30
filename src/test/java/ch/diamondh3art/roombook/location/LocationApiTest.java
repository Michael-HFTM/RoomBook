package ch.diamondh3art.roombook.location;

import ch.diamondh3art.roombook.TestcontainersConfiguration;

import ch.diamondh3art.roombook.common.user.AppUser;
import ch.diamondh3art.roombook.common.user.AppUserRepository;
import ch.diamondh3art.roombook.common.user.Role;
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
 * A1 via REST: admins create, update and deactivate locations; auth (401/403) and error mapping (400/404).
 * Each test runs in a transaction that is rolled back afterwards.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocationApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    AppUserRepository appUserRepository;

    long adminId;
    long userId;

    @BeforeEach
    void setUp() {
        adminId = appUserRepository.save(new AppUser("admin", Role.ADMIN)).getId();
        userId = appUserRepository.save(new AppUser("user", Role.USER)).getId();
    }

    @Test
    void adminCreatesHierarchy() {
        long branch = create(null, "Bern", "BRANCH");

        MvcTestResult result = post(adminId, body(branch, "Building A", "BUILDING"));

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.parentId").asNumber().isEqualTo((int) branch);
        assertThat(result).bodyJson().extractingPath("$.active").isEqualTo(true);
    }

    @Test
    void missingHeaderIsUnauthorized() {
        assertThat(mvc.post().uri("/api/locations").contentType(MediaType.APPLICATION_JSON)
                .content(body(null, "Bern", "BRANCH"))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownUserIsUnauthorized() {
        assertThat(post(-1, body(null, "Bern", "BRANCH"))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userWithoutAdminRoleIsForbidden() {
        assertThat(post(userId, body(null, "Bern", "BRANCH"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidRequestIsBadRequest() {
        assertThat(post(adminId, body(null, " ", "BRANCH"))).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknownParentIsNotFound() {
        assertThat(post(adminId, body(-1L, "Building A", "BUILDING"))).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminUpdatesLocation() {
        long bern = create(null, "Bern", "BRANCH");
        long zurich = create(null, "Zurich", "BRANCH");
        long building = create(bern, "Building A", "BUILDING");

        MvcTestResult result = put(adminId, building, body(zurich, "Building B", "BUILDING"));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.parentId").asNumber().isEqualTo((int) zurich);
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Building B");
    }

    @Test
    void movingBelowOwnDescendantIsRejectedByDb() {
        long branch = create(null, "Bern", "BRANCH");
        long building = create(branch, "Building A", "BUILDING");
        long floor = create(building, "Floor 1", "FLOOR");

        MvcTestResult result = put(adminId, branch, body(floor, "Bern", "BRANCH"));

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.constraint").isEqualTo("ck_location_no_cycle");
    }

    @Test
    void adminDeactivatesLocation() {
        long branch = create(null, "Bern", "BRANCH");

        assertThat(mvc.delete().uri("/api/locations/{id}", branch).header("X-User-Id", adminId))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/locations/{id}", branch))
                .bodyJson().extractingPath("$.active").isEqualTo(false);
    }

    @Test
    void unknownLocationIsNotFound() {
        assertThat(mvc.get().uri("/api/locations/{id}", -1)).hasStatus(HttpStatus.NOT_FOUND);
    }

    private long create(Long parentId, String name, String type) {
        MvcTestResult result = post(adminId, body(parentId, name, type));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.<Number>read(new String(result.getResponse().getContentAsByteArray()), "$.id").longValue();
    }

    private MvcTestResult post(long asUser, String json) {
        return mvc.post().uri("/api/locations").header("X-User-Id", asUser)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private MvcTestResult put(long asUser, long id, String json) {
        return mvc.put().uri("/api/locations/{id}", id).header("X-User-Id", asUser)
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static String body(Long parentId, String name, String type) {
        return """
                {"parentId": %s, "name": "%s", "type": "%s"}""".formatted(parentId, name, type);
    }
}

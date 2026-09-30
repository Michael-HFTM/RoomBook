package ch.diamondh3art.roombook.location;

import ch.diamondh3art.roombook.TestcontainersConfiguration;
import ch.diamondh3art.roombook.common.user.AppUser;
import ch.diamondh3art.roombook.common.user.AppUserRepository;
import ch.diamondh3art.roombook.common.user.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * T12: the location subtree (A6 filter) is cached per location id and must be current after every committed
 * hierarchy change. The spy counts how often the recursive CTE actually runs. Runs without a test transaction,
 * because eviction happens after commit; test data is removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LocationSubtreeCacheTest {

    @Autowired
    LocationService locationService;

    @MockitoSpyBean
    LocationRepository locationRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    JdbcClient jdbc;

    final List<Long> created = new ArrayList<>();
    long adminId;
    long branch;
    long buildingA;
    long buildingB;
    long floor;

    @BeforeEach
    void setUp() {
        adminId = appUserRepository.save(new AppUser("cache-admin", Role.ADMIN)).getId();
        branch = create(null, "Cache Branch", LocationType.BRANCH);
        buildingA = create(branch, "Building A", LocationType.BUILDING);
        buildingB = create(branch, "Building B", LocationType.BUILDING);
        floor = create(buildingA, "Floor 1", LocationType.FLOOR);
        clearInvocations(locationRepository);
    }

    @AfterEach
    void cleanUp() {
        created.reversed().forEach(id -> jdbc.sql("DELETE FROM location WHERE location_id = ?").param(id).update());
        jdbc.sql("DELETE FROM app_user WHERE app_user_id = ?").param(adminId).update();
    }

    @Test
    void firstAccessQueriesThenHitsCacheAndMoveReloadsCurrentSubtree() {
        // first access: query
        assertThat(locationService.findSubtreeIds(buildingA)).containsExactlyInAnyOrder(buildingA, floor);
        verify(locationRepository, times(1)).findSubtreeIds(buildingA);

        // repeated access: cache hit, no further query
        assertThat(locationService.findSubtreeIds(buildingA)).containsExactlyInAnyOrder(buildingA, floor);
        verify(locationRepository, times(1)).findSubtreeIds(buildingA);

        // relevant change: move the floor to building B → both old and new ancestor are current
        locationService.update(adminId, floor, new LocationRequest(buildingB, "Floor 1", LocationType.FLOOR));

        assertThat(locationService.findSubtreeIds(buildingA)).containsExactly(buildingA);
        assertThat(locationService.findSubtreeIds(buildingB)).containsExactlyInAnyOrder(buildingB, floor);
        verify(locationRepository, times(2)).findSubtreeIds(buildingA);
    }

    @Test
    void newChildIsVisibleInCachedAncestorSubtree() {
        assertThat(locationService.findSubtreeIds(branch)).containsExactlyInAnyOrder(branch, buildingA, buildingB, floor);

        long annex = create(branch, "Annex", LocationType.BUILDING);

        assertThat(locationService.findSubtreeIds(branch)).contains(annex);
    }

    @Test
    void deactivationKeepsCacheBecauseSubtreeIdsDoNotChange() {
        locationService.findSubtreeIds(buildingA);

        locationService.deactivate(adminId, floor);

        assertThat(locationService.findSubtreeIds(buildingA)).containsExactlyInAnyOrder(buildingA, floor);
        verify(locationRepository, times(1)).findSubtreeIds(buildingA);
    }

    @Test
    void rolledBackChangeKeepsCache() {
        locationService.findSubtreeIds(branch);

        // cycle: the DB trigger rejects moving the branch below its own floor, the transaction rolls back
        assertThatThrownBy(() -> locationService.update(adminId, branch,
                new LocationRequest(floor, "Cache Branch", LocationType.BRANCH)))
                .isInstanceOf(DataAccessException.class);

        assertThat(locationService.findSubtreeIds(branch)).containsExactlyInAnyOrder(branch, buildingA, buildingB, floor);
        verify(locationRepository, times(1)).findSubtreeIds(branch);
    }

    private long create(Long parentId, String name, LocationType type) {
        long id = locationService.create(adminId, new LocationRequest(parentId, name, type)).id();
        created.add(id);
        return id;
    }
}

package ch.diamondh3art.roombook.location;

import ch.diamondh3art.roombook.common.user.UserService;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LocationService {

    static final String SUBTREE_CACHE = "locationSubtree";

    private final LocationRepository locationRepository;
    private final UserService userService;
    private final Cache subtreeCache;

    public LocationService(LocationRepository locationRepository, UserService userService, CacheManager cacheManager) {
        this.locationRepository = locationRepository;
        this.userService = userService;
        this.subtreeCache = cacheManager.getCache(SUBTREE_CACHE);
    }

    public List<LocationResponse> findAll() {
        return locationRepository.findAll(Sort.by("id")).stream().map(LocationResponse::from).toList();
    }

    public LocationResponse findById(long id) {
        return LocationResponse.from(getLocation(id));
    }

    @Transactional
    public LocationResponse create(long userId, LocationRequest request) {
        userService.requireAdmin(userId);
        Location location = new Location(getParent(request.parentId()), request.name(), request.type());
        evictSubtreesAfterCommit();
        // flush so DB constraint violations surface here and are mapped to HTTP errors
        return LocationResponse.from(locationRepository.saveAndFlush(location));
    }

    @Transactional
    public LocationResponse update(long userId, long id, LocationRequest request) {
        userService.requireAdmin(userId);
        Location location = getLocation(id);
        location.update(getParent(request.parentId()), request.name(), request.type());
        evictSubtreesAfterCommit();
        // cycle check runs in the DB trigger on flush (A1)
        locationRepository.flush();
        return LocationResponse.from(location);
    }

    @Transactional
    public void deactivate(long userId, long id) {
        userService.requireAdmin(userId);
        getLocation(id).deactivate();
    }

    // subtree ids for filters on a location including all sub-levels (A6); cached per location id (T12),
    // unknown ids throw and are not cached
    @Cacheable(SUBTREE_CACHE)
    public List<Long> findSubtreeIds(long id) {
        List<Long> ids = locationRepository.findSubtreeIds(id);
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Location " + id + " not found");
        }
        // immutable, callers share the cached instance
        return List.copyOf(ids);
    }

    // a location only counts as active if all its ancestors are active too
    public boolean isActiveWithAncestors(Location location) {
        return locationRepository.isActiveWithAncestors(location.getId());
    }

    // also used by RoomService to resolve the location of a room
    public Location getLocation(long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location " + id + " not found"));
    }

    // T12: creating or moving a node changes the subtrees of all its (old and new) ancestors, so the whole cache is
    // cleared. Only after commit: clearing earlier would let a concurrent read cache the old hierarchy again, and a
    // rollback leaves the hierarchy unchanged. Deactivating does not change subtree ids and keeps the cache.
    private void evictSubtreesAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                subtreeCache.clear();
            }
        });
    }

    private Location getParent(Long parentId) {
        return parentId == null ? null : getLocation(parentId);
    }
}

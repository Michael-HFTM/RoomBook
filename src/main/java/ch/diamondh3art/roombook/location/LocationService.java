package ch.diamondh3art.roombook.location;

import ch.diamondh3art.roombook.common.user.UserService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LocationService {

    private final LocationRepository locationRepository;
    private final UserService userService;

    public LocationService(LocationRepository locationRepository, UserService userService) {
        this.locationRepository = locationRepository;
        this.userService = userService;
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
        // flush so DB constraint violations surface here and are mapped to HTTP errors
        return LocationResponse.from(locationRepository.saveAndFlush(location));
    }

    @Transactional
    public LocationResponse update(long userId, long id, LocationRequest request) {
        userService.requireAdmin(userId);
        Location location = getLocation(id);
        location.update(getParent(request.parentId()), request.name(), request.type());
        // cycle check runs in the DB trigger on flush (A1)
        locationRepository.flush();
        return LocationResponse.from(location);
    }

    @Transactional
    public void deactivate(long userId, long id) {
        userService.requireAdmin(userId);
        getLocation(id).deactivate();
    }

    // also used by RoomService to resolve the location of a room
    public Location getLocation(long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Location " + id + " not found"));
    }

    private Location getParent(Long parentId) {
        return parentId == null ? null : getLocation(parentId);
    }
}

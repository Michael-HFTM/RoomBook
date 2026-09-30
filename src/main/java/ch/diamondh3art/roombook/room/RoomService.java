package ch.diamondh3art.roombook.room;

import ch.diamondh3art.roombook.common.user.UserService;
import ch.diamondh3art.roombook.location.LocationService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final LocationService locationService;
    private final UserService userService;

    public RoomService(RoomRepository roomRepository, LocationService locationService, UserService userService) {
        this.roomRepository = roomRepository;
        this.locationService = locationService;
        this.userService = userService;
    }

    public List<RoomResponse> findAll() {
        return roomRepository.findAll(Sort.by("id")).stream().map(RoomResponse::from).toList();
    }

    // A3
    public List<RoomResponse> findFree(OffsetDateTime from, OffsetDateTime to, int minCapacity, Long locationId) {
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must be before 'to'");
        }
        if (locationId != null) {
            locationService.getLocation(locationId);
        }
        return roomRepository.findFree(locationId, minCapacity, from, to).stream().map(RoomResponse::from).toList();
    }

    public RoomResponse findById(long id) {
        return RoomResponse.from(getRoom(id));
    }

    @Transactional
    public RoomResponse create(long userId, RoomRequest request) {
        userService.requireAdmin(userId);
        Room room = new Room(locationService.getLocation(request.locationId()), request.name(), request.capacity());
        // flush so the unique name per location (uq_room_location_id_name) surfaces here as 409
        return RoomResponse.from(roomRepository.saveAndFlush(room));
    }

    @Transactional
    public RoomResponse update(long userId, long id, RoomRequest request) {
        userService.requireAdmin(userId);
        Room room = getRoom(id);
        room.update(locationService.getLocation(request.locationId()), request.name(), request.capacity());
        roomRepository.flush();
        return RoomResponse.from(room);
    }

    @Transactional
    public void deactivate(long userId, long id) {
        userService.requireAdmin(userId);
        getRoom(id).deactivate();
    }

    public Room getRoom(long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room " + id + " not found"));
    }
}

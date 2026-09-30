package ch.diamondh3art.roombook.room;

import ch.diamondh3art.roombook.common.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomResponse> findAll() {
        return roomService.findAll();
    }

    @GetMapping("/{id}")
    public RoomResponse findById(@PathVariable long id) {
        return roomService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@RequestHeader(UserService.USER_HEADER) long userId,
                                   @Valid @RequestBody RoomRequest request) {
        return roomService.create(userId, request);
    }

    @PutMapping("/{id}")
    public RoomResponse update(@RequestHeader(UserService.USER_HEADER) long userId, @PathVariable long id,
                                   @Valid @RequestBody RoomRequest request) {
        return roomService.update(userId, id, request);
    }

    // soft delete: sets active = false, the row stays for history and reports
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@RequestHeader(UserService.USER_HEADER) long userId, @PathVariable long id) {
        roomService.deactivate(userId, id);
    }
}

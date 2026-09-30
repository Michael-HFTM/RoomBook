package ch.diamondh3art.roombook.room;

import ch.diamondh3art.roombook.common.user.UserService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
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

    // A3: e.g. /api/rooms/free?from=2026-10-05T09:00:00Z&to=2026-10-05T10:00:00Z&minCapacity=4&locationId=1
    @GetMapping("/free")
    public List<RoomResponse> findFree(@Parameter(example = "2026-10-05T09:00:00+02:00", description = "ISO-8601 with offset, e.g. +02:00 (summer) or +01:00 (winter) for Zurich local time")
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                                       @Parameter(example = "2026-10-05T10:00:00+02:00", description = "ISO-8601 with offset, e.g. +02:00 (summer) or +01:00 (winter) for Zurich local time")
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
                                       @RequestParam(defaultValue = "1") @Positive int minCapacity,
                                       @RequestParam(required = false) Long locationId) {
        return roomService.findFree(from, to, minCapacity, locationId);
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

package ch.diamondh3art.roombook.location;

import ch.diamondh3art.roombook.common.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public List<LocationResponse> findAll() {
        return locationService.findAll();
    }

    @GetMapping("/{id}")
    public LocationResponse findById(@PathVariable long id) {
        return locationService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LocationResponse create(@RequestHeader(UserService.USER_HEADER) long userId,
                                   @Valid @RequestBody LocationRequest request) {
        return locationService.create(userId, request);
    }

    @PutMapping("/{id}")
    public LocationResponse update(@RequestHeader(UserService.USER_HEADER) long userId, @PathVariable long id,
                                   @Valid @RequestBody LocationRequest request) {
        return locationService.update(userId, id, request);
    }

    // soft delete: sets active = false, the row stays for history and reports
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@RequestHeader(UserService.USER_HEADER) long userId, @PathVariable long id) {
        locationService.deactivate(userId, id);
    }
}

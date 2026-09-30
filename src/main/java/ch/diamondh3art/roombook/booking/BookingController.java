package ch.diamondh3art.roombook.booking;

import ch.diamondh3art.roombook.common.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/{id}")
    public BookingResponse findById(@PathVariable long id) {
        return bookingService.findById(id);
    }

    // returns one entry for a single booking, all occurrences for a series
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<BookingResponse> create(@RequestHeader(UserService.USER_HEADER) long userId,
                                        @Valid @RequestBody BookingRequest request) {
        return bookingService.create(userId, request);
    }

    @PutMapping("/{id}")
    public BookingResponse update(@RequestHeader(UserService.USER_HEADER) long userId, @PathVariable long id,
                                  @Valid @RequestBody BookingUpdateRequest request) {
        return bookingService.update(userId, id, request);
    }

    // cancels the booking (status CANCELLED), no physical delete
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@RequestHeader(UserService.USER_HEADER) long userId, @PathVariable long id) {
        bookingService.cancel(userId, id);
    }
}

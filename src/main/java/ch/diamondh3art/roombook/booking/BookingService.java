package ch.diamondh3art.roombook.booking;

import ch.diamondh3art.roombook.common.user.AppUser;
import ch.diamondh3art.roombook.common.user.UserService;
import ch.diamondh3art.roombook.room.Room;
import ch.diamondh3art.roombook.room.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BookingService {

    // series repeat at the same local time, also across DST changes
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Zurich");

    private final BookingRepository bookingRepository;
    private final BookingSeriesRepository bookingSeriesRepository;
    private final RoomService roomService;
    private final UserService userService;

    public BookingService(BookingRepository bookingRepository, BookingSeriesRepository bookingSeriesRepository,
                          RoomService roomService, UserService userService) {
        this.bookingRepository = bookingRepository;
        this.bookingSeriesRepository = bookingSeriesRepository;
        this.roomService = roomService;
        this.userService = userService;
    }

    public BookingResponse findById(long id) {
        return BookingResponse.from(getBooking(id));
    }

    // A4, T4: series and all occurrences in one transaction, the first overlap rolls back everything
    @Transactional
    public List<BookingResponse> create(long userId, BookingRequest request) {
        AppUser user = userService.requireUser(userId);
        Room room = roomService.getRoom(request.roomId());
        OffsetDateTime start = toMinute(request.startTime());
        OffsetDateTime end = toMinute(request.endTime());
        validate(room, start, end);

        BookingSeries series = request.occurrences() == null ? null
                : bookingSeriesRepository.save(new BookingSeries(user, RecurrenceRule.WEEKLY, request.occurrences()));
        int count = series == null ? 1 : series.getOccurrences();

        List<BookingResponse> created = new ArrayList<>(count);
        for (int week = 0; week < count; week++) {
            Booking booking = new Booking(room, user, series, plusWeeks(start, week), plusWeeks(end, week),
                    request.title());
            // flush each occurrence so an overlap (ex_booking_room_id) fails here and not only at commit
            created.add(BookingResponse.from(bookingRepository.saveAndFlush(booking)));
        }
        return created;
    }

    // A5: only the booker or an admin; the client's version detects lost updates across requests
    @Transactional
    public BookingResponse update(long userId, long id, BookingUpdateRequest request) {
        Booking booking = getOwnedBooking(userId, id);
        if (booking.getVersion() != request.version()) {
            throw new ObjectOptimisticLockingFailureException(Booking.class, id);
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled booking cannot be changed");
        }
        OffsetDateTime start = toMinute(request.startTime());
        OffsetDateTime end = toMinute(request.endTime());
        validate(booking.getRoom(), start, end);
        booking.update(start, end, request.title());
        // @Version check and exclusion constraint run on flush
        bookingRepository.flush();
        return BookingResponse.from(booking);
    }

    // A5: cancelling sets the status, the row is kept for history and reports
    @Transactional
    public void cancel(long userId, long id) {
        getOwnedBooking(userId, id).cancel();
        bookingRepository.flush();
    }

    private Booking getOwnedBooking(long userId, long id) {
        AppUser user = userService.requireUser(userId);
        Booking booking = getBooking(id);
        if (!booking.isOwnedBy(user.getId()) && !user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the booker or an administrator may change this booking");
        }
        return booking;
    }

    private Booking getBooking(long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking " + id + " not found"));
    }

    // overlaps are left to the exclusion constraint, it also covers concurrent requests (T5)
    private static void validate(Room room, OffsetDateTime start, OffsetDateTime end) {
        if (!room.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room " + room.getId() + " is deactivated");
        }
        if (!start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time must be before end time");
        }
        if (start.isBefore(OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time must not be in the past");
        }
    }

    // seconds and fractions are cut off (decision 2026-09-30)
    private static OffsetDateTime toMinute(OffsetDateTime time) {
        return time.truncatedTo(ChronoUnit.MINUTES);
    }

    private static OffsetDateTime plusWeeks(OffsetDateTime time, int weeks) {
        return time.atZoneSameInstant(BUSINESS_ZONE).plusWeeks(weeks).toOffsetDateTime();
    }
}

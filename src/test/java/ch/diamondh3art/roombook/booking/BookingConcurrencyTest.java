package ch.diamondh3art.roombook.booking;

import ch.diamondh3art.roombook.TestcontainersConfiguration;
import ch.diamondh3art.roombook.common.user.AppUser;
import ch.diamondh3art.roombook.common.user.AppUserRepository;
import ch.diamondh3art.roombook.common.user.Role;
import ch.diamondh3art.roombook.location.Location;
import ch.diamondh3art.roombook.location.LocationRepository;
import ch.diamondh3art.roombook.location.LocationType;
import ch.diamondh3art.roombook.room.Room;
import ch.diamondh3art.roombook.room.RoomRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T4 (atomic series) and T5 (concurrent bookings and changes). Runs without a test transaction so every
 * service call commits for real; test data is removed after each test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookingConcurrencyTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2030-11-04T10:00:00+01:00");
    private static final OffsetDateTime END = START.plusHours(1);

    @Autowired
    BookingService bookingService;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    LocationRepository locationRepository;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    long userA;
    long userB;
    long locationId;
    long roomId;

    @BeforeEach
    void setUp() {
        userA = appUserRepository.save(new AppUser("concurrency-a", Role.USER)).getId();
        userB = appUserRepository.save(new AppUser("concurrency-b", Role.ADMIN)).getId();
        Location location = locationRepository.save(new Location(null, "Concurrency", LocationType.FLOOR));
        locationId = location.getId();
        roomId = roomRepository.save(new Room(location, "Room 1", 8)).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM booking WHERE room_id = ?").param(roomId).update();
        jdbc.sql("DELETE FROM booking_series WHERE app_user_id IN (?, ?)").params(userA, userB).update();
        jdbc.sql("DELETE FROM room WHERE room_id = ?").param(roomId).update();
        jdbc.sql("DELETE FROM location WHERE location_id = ?").param(locationId).update();
        jdbc.sql("DELETE FROM app_user WHERE app_user_id IN (?, ?)").params(userA, userB).update();
    }

    // T4: the 3rd occurrence overlaps an existing booking → neither the series nor occurrences 1 and 2 remain
    @Test
    void seriesIsRolledBackCompletelyWhenOneOccurrenceOverlaps() {
        bookingService.create(userB, request(START.plusWeeks(2), END.plusWeeks(2), null));

        assertThatThrownBy(() -> bookingService.create(userA, request(START, END, 5)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(count("SELECT count(*) FROM booking_series WHERE app_user_id = ?", userA)).isZero();
        assertThat(count("SELECT count(*) FROM booking WHERE app_user_id = ?", userA)).isZero();
        assertThat(count("SELECT count(*) FROM booking WHERE room_id = ?", roomId)).isEqualTo(1);
    }

    // T5: two users book the same slot at the same time, the exclusion constraint lets exactly one win
    @Test
    void concurrentOverlappingBookingsOnlyOneWins() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = runConcurrently(
                () -> { start.await(); bookingService.create(userA, request(START, END, null)); },
                () -> { start.await(); bookingService.create(userB, request(START.plusMinutes(30), END, null)); },
                start::countDown);

        assertThat(failures).singleElement().isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("SELECT count(*) FROM booking WHERE room_id = ?", roomId)).isEqualTo(1);
    }

    // T5: both transactions read version 0 before either writes; @Version rejects the second commit
    @Test
    void concurrentChangesAreDetectedByOptimisticLock() throws Exception {
        long id = bookingService.create(userA, request(START, END, null)).getFirst().id();
        CyclicBarrier bothRead = new CyclicBarrier(2);

        List<Throwable> failures = runConcurrently(
                () -> change(id, "by user", bothRead),
                () -> change(id, "by admin", bothRead),
                () -> { });

        assertThat(failures).singleElement().isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(bookingRepository.findById(id).orElseThrow().getVersion()).isEqualTo(1);
    }

    private void change(long id, String title, CyclicBarrier bothRead) {
        tx.executeWithoutResult(status -> {
            Booking booking = bookingRepository.findById(id).orElseThrow();
            await(bothRead);
            booking.update(booking.getStartTime(), booking.getEndTime(), title);
        });
    }

    // runs both tasks in parallel, then trigger; returns the exceptions of the failed tasks
    private static List<Throwable> runConcurrently(Task first, Task second, Task trigger) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = List.of(executor.submit(first::toCallable), executor.submit(second::toCallable));
            trigger.run();
            List<Throwable> failures = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        }
    }

    @FunctionalInterface
    private interface Task {
        void run() throws Exception;

        default Void toCallable() throws Exception {
            run();
            return null;
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long count(String sql, long param) {
        return jdbc.sql(sql).param(param).query(Long.class).single();
    }

    private BookingRequest request(OffsetDateTime start, OffsetDateTime end, Integer occurrences) {
        return new BookingRequest(roomId, start, end, "Meeting", occurrences);
    }
}

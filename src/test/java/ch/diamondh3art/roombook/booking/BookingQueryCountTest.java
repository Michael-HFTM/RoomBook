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
import jakarta.persistence.EntityManager;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T11 (and T7): counts the SQL statements of the A6 booking list for 1, 10 and 100 results, each booking with its
 * own room and user. Before: loading Booking entities and reading room/user names (lazy associations) → N+1.
 * After: the JPQL DTO projection used by the API → constant number of queries, page limit applied in SQL.
 */
@Import({TestcontainersConfiguration.class, BookingQueryCountTest.QueryLog.class})
@SpringBootTest
@Transactional
class BookingQueryCountTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2030-01-07T09:00:00Z");
    private static final int PAGE_SIZE = 100;

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
    EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void entityLoadingCausesNPlusOne(int n) {
        createBookings(n);

        List<BookingListItem> items = bookingRepository.findAll(PageRequest.of(0, PAGE_SIZE, Sort.by("startTime", "id")))
                .map(b -> new BookingListItem(b.getId(), b.getRoom().getId(), b.getRoom().getName(),
                        b.getUser().getId(), b.getUser().getUsername(), b.getStartTime(), b.getEndTime(),
                        b.getTitle(), b.getStatus()))
                .getContent();

        assertThat(items).hasSize(n);
        // 1 page query + 1 query per room + 1 query per user (+ count query when the page is full)
        assertThat(selects()).hasSize(1 + 2 * n + countQuery(n));
        report("entities", n);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void dtoProjectionUsesConstantNumberOfQueries(int n) {
        createBookings(n);

        var page = bookingService.search(new BookingFilter(null, null, null, null, null, null), 0, PAGE_SIZE);

        assertThat(page.getContent()).hasSize(n);
        assertThat(selects()).hasSize(1 + countQuery(n));
        // T7: paging happens in the database, not in memory
        assertThat(selects().getFirst()).containsIgnoringCase("fetch first");
        report("DTO projection", n);
    }

    // Spring Data skips the count query if the first page is not full
    private static int countQuery(int n) {
        return n == PAGE_SIZE ? 1 : 0;
    }

    private void createBookings(int n) {
        Location floor = locationRepository.save(new Location(null, "Floor", LocationType.FLOOR));
        for (int i = 0; i < n; i++) {
            Room room = roomRepository.save(new Room(floor, "Room " + i, 4));
            AppUser user = appUserRepository.save(new AppUser("user" + i, Role.USER));
            bookingRepository.save(new Booking(room, user, null, START.plusHours(i), START.plusHours(i + 1), "Meeting"));
        }
        // start with an empty persistence context, otherwise rooms and users would come from the first-level cache
        entityManager.flush();
        entityManager.clear();
        QueryLog.SQL.clear();
    }

    private static List<String> selects() {
        return QueryLog.SQL.stream().filter(sql -> sql.stripLeading().toLowerCase().startsWith("select")).toList();
    }

    private static void report(String variant, int n) {
        System.out.printf("T11 %-15s results=%3d queries=%3d%n", variant, n, selects().size());
    }

    // wraps the DataSource with datasource-proxy and records every executed statement
    @TestConfiguration(proxyBeanMethods = false)
    static class QueryLog {

        static final List<String> SQL = new CopyOnWriteArrayList<>();

        @Bean
        static BeanPostProcessor queryLoggingDataSource() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof DataSource dataSource && !(bean instanceof ProxyDataSource)) {
                        return ProxyDataSourceBuilder.create(dataSource)
                                .afterQuery((exec, queries) -> queries.forEach(q -> SQL.add(q.getQuery())))
                                .build();
                    }
                    return bean;
                }
            };
        }
    }
}

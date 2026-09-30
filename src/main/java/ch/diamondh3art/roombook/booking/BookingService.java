package ch.diamondh3art.roombook.booking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeriesRepository bookingSeriesRepository;

    public BookingService(BookingRepository bookingRepository, BookingSeriesRepository bookingSeriesRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingSeriesRepository = bookingSeriesRepository;
    }
}

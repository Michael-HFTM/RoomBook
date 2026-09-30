package ch.diamondh3art.roombook.booking;

import ch.diamondh3art.roombook.common.user.AppUser;
import ch.diamondh3art.roombook.room.Room;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser user;

    // null for single bookings
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_series_id")
    private BookingSeries series;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.ACTIVE;

    // optimistic locking (A5, T5)
    @Version
    @Column(nullable = false)
    private int version;

    protected Booking() {
    }

    public Booking(Room room, AppUser user, BookingSeries series,
                   OffsetDateTime startTime, OffsetDateTime endTime) {
        this.room = room;
        this.user = user;
        this.series = series;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }

    public void reschedule(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (status == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled booking " + id + " cannot be rescheduled");
        }
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public Long getId() { return id; }
    public Room getRoom() { return room; }
    public AppUser getUser() { return user; }
    public BookingSeries getSeries() { return series; }
    public OffsetDateTime getStartTime() { return startTime; }
    public OffsetDateTime getEndTime() { return endTime; }
    public BookingStatus getStatus() { return status; }
    public int getVersion() { return version; }
}
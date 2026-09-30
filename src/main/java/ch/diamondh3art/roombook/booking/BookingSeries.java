package ch.diamondh3art.roombook.booking;

import ch.diamondh3art.roombook.common.user.AppUser;
import jakarta.persistence.*;

@Entity
@Table(name = "booking_series")
public class BookingSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_series_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule", nullable = false, length = 20)
    private RecurrenceRule rule;

    @Column(name = "occurrences", nullable = false)
    private int occurrences;

    protected BookingSeries() {
    }

    public BookingSeries(AppUser user, RecurrenceRule rule, int occurrences) {
        this.user = user;
        this.rule = rule;
        this.occurrences = occurrences;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public RecurrenceRule getRule() { return rule; }
    public int getOccurrences() { return occurrences; }
}

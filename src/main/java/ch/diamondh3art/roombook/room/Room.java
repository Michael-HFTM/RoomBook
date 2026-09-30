package ch.diamondh3art.roombook.room;

import ch.diamondh3art.roombook.location.Location;
import jakarta.persistence.*;

@Entity
@Table(name = "room")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Room() {
    }

    public Room(Location location, String name, int capacity) {
        this.location = location;
        this.name = name;
        this.capacity = capacity;
    }

    public void update(Location location, String name, int capacity) {
        this.location = location;
        this.name = name;
        this.capacity = capacity;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() { return id; }
    public Location getLocation() { return location; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }
    public boolean isActive() { return active; }
}

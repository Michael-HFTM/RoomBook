package ch.diamondh3art.roombook.location;

import jakarta.persistence.*;

@Entity
@Table(name = "location")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_location_id")
    private Location parent;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private LocationType type;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Location() {
    }

    public Location(Location parent, String name, LocationType type) {
        this.parent = parent;
        this.name = name;
        this.type = type;
    }

    // cycle check is done by a DB trigger (A1)
    public void update(Location parent, String name, LocationType type) {
        this.parent = parent;
        this.name = name;
        this.type = type;
    }

    public void deactivate() {
        this.active = false;
    }

    public Long getId() { return id; }
    public Location getParent() { return parent; }
    public String getName() { return name; }
    public LocationType getType() { return type; }
    public boolean isActive() { return active; }
}

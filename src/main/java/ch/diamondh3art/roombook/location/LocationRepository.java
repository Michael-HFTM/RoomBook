package ch.diamondh3art.roombook.location;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {

    // location itself and all descendants, active or not (A6, A7); UNION ALL is safe, the DB prevents cycles
    @Query(value = """
            WITH RECURSIVE subtree (location_id) AS (
                SELECT location_id FROM location WHERE location_id = :id
                UNION ALL
                SELECT l.location_id
                FROM location l
                         JOIN subtree s ON l.parent_location_id = s.location_id)
            SELECT location_id FROM subtree""", nativeQuery = true)
    List<Long> findSubtreeIds(long id);

    // true if the location and all its ancestors are active; a deactivated node hides its whole subtree (A4)
    @Query(value = """
            WITH RECURSIVE ancestors (parent_location_id, active) AS (
                SELECT parent_location_id, active FROM location WHERE location_id = :id
                UNION ALL
                SELECT l.parent_location_id, l.active
                FROM location l
                         JOIN ancestors a ON l.location_id = a.parent_location_id)
            SELECT bool_and(active) FROM ancestors""", nativeQuery = true)
    boolean isActiveWithAncestors(long id);
}

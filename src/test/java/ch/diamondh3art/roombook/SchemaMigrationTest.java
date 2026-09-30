package ch.diamondh3art.roombook;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the schema evolution V1 → V2 on a database that already contains data (T2).
 * Uses its own container without Spring, so Flyway can be stopped at a given version.
 */
@Testcontainers
class SchemaMigrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    @Test
    void v2BackfillsTitleOfExistingBookings() {
        DataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcClient jdbc = JdbcClient.create(dataSource);

        migrateTo(dataSource, "1");
        long locationId = jdbc.sql("INSERT INTO location (name, type) VALUES ('Building', 'BUILDING') RETURNING location_id").query(Long.class).single();
        long roomId = jdbc.sql("INSERT INTO room (location_id, name, capacity) VALUES (?, 'Room 1', 10) RETURNING room_id").params(locationId).query(Long.class).single();
        long userId = jdbc.sql("INSERT INTO app_user (username, role) VALUES ('tester', 'USER') RETURNING app_user_id").query(Long.class).single();
        jdbc.sql("""
                INSERT INTO booking (room_id, app_user_id, start_time, end_time)
                VALUES (?, ?, '2026-10-05 10:00+02', '2026-10-05 12:00+02')
                """).params(roomId, userId).update();

        migrateTo(dataSource, "2");

        assertThat(jdbc.sql("SELECT title FROM booking").query(String.class).list()).containsExactly("Room 1");
        assertThat(jdbc.sql("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_name = 'booking' AND column_name = 'title'
                """).query(String.class).single()).isEqualTo("NO");
    }

    private static void migrateTo(DataSource dataSource, String version) {
        Flyway.configure().dataSource(dataSource).target(version).load().migrate();
    }
}

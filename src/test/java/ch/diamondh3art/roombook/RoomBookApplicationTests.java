package ch.diamondh3art.roombook;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RoomBookApplicationTests {

    @Test
    void contextLoads() {
    }

}

package ch.diamondh3art.roombook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class RoomBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoomBookApplication.class, args);
    }

}

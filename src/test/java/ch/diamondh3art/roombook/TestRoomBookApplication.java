package ch.diamondh3art.roombook;

import org.springframework.boot.SpringApplication;

public class TestRoomBookApplication {

    public static void main(String[] args) {
        SpringApplication.from(RoomBookApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}

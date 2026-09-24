package ru.library.event;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LibraryEventApplication {
    public static void main(String[] args) {
        SpringApplication.run(LibraryEventApplication.class, args);
    }
}

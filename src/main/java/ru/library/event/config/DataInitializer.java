package ru.library.event.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.library.event.model.Event;
import ru.library.event.service.EventService;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final EventService eventService;

    public DataInitializer(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!eventService.getAllEvents().isEmpty()) {
            return;
        }

        eventService.createEvent(new Event(
                "Война и мир",
                "Роман-эпопея Л.Н. Толстого",
                "Лев Толстой",
                "Классическая литература",
                5
        ));
        eventService.createEvent(new Event(
                "Преступление и наказание",
                "Роман Ф.М. Достоевского",
                "Фёдор Достоевский",
                "Классическая литература",
                3
        ));
        eventService.createEvent(new Event(
                "Мастер и Маргарита",
                "Роман М.А. Булгакова",
                "Михаил Булгаков",
                "Советская литература",
                4
        ));
        eventService.createEvent(new Event(
                "1984",
                "Антиутопия Дж. Оруэлла",
                "Джордж Оруэлл",
                "Зарубежная литература",
                2
        ));
        eventService.createEvent(new Event(
                "Мастерство программирования",
                "Практическое руководство",
                "Роберт Мартин",
                "Информатика",
                6
        ));
        log.info("Demo data initialized in PostgreSQL");
    }
}

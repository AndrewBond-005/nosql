package ru.library.event.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import ru.library.event.model.Event;
import ru.library.event.model.Manager;
import ru.library.event.service.EventService;
import ru.library.event.service.ManagerService;

@Component
@Profile("inmemory")
public class DataInitializer implements CommandLineRunner {

    private final EventService eventService;
    private final ManagerService managerService;

    @Autowired
    public DataInitializer(EventService eventService, ManagerService managerService) {
        this.eventService = eventService;
        this.managerService = managerService;
    }

    @Override
    public void run(String... args) {
        Manager manager1 = new Manager("Иван Иванов", "ivanov@library.ru");
        Manager manager2 = new Manager("Петр Петров", "petrov@library.ru");
        managerService.createManager(manager1);
        managerService.createManager(manager2);

        Event event1 = new Event(
                "Война и мир",
                "Роман-эпопея Л.Н. Толстого",
                "Лев Толстой",
                "Классическая литература",
                5
        );
        eventService.createEvent(event1);

        Event event2 = new Event(
                "Преступление и наказание",
                "Роман Ф.М. Достоевского",
                "Фёдор Достоевский",
                "Классическая литература",
                3
        );
        eventService.createEvent(event2);

        Event event3 = new Event(
                "Мастер и Маргарита",
                "Роман М.А. Булгакова",
                "Михаил Булгаков",
                "Советская литература",
                4
        );
        eventService.createEvent(event3);

        Event event4 = new Event(
                "1984",
                "Антиутопия Дж. Оруэлла",
                "Джордж Оруэлл",
                "Зарубежная литература",
                2
        );
        eventService.createEvent(event4);

        Event event5 = new Event(
                "Мастерство программирования",
                "Практическое руководство",
                "Роберт Мартин",
                "Информатика",
                6
        );
        eventService.createEvent(event5);

        System.out.println("=== Демонстрационные данные загружены ===");
        System.out.println("Менеджеры: " + managerService.getAllManagers().size());
        System.out.println("События: " + eventService.getAllEvents().size());
    }
}

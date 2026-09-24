package ru.library.event.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.library.event.model.CreateEventRequest;
import ru.library.event.model.Event;
import ru.library.event.service.EventService;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*")
public class EventController {

    private final EventService eventService;

    @Autowired
    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<Event> createEvent(@RequestBody CreateEventRequest request) {
        Event event = new Event(
                request.getTitle(),
                request.getDescription(),
                request.getAuthor(),
                request.getCategory(),
                request.getAvailableCopies()
        );
        Event created = eventService.createEvent(event);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Event> getEvent(@PathVariable String id) {
        return eventService.getEvent(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Event>> getAllEvents() {
        return ResponseEntity.ok(eventService.getAllEvents());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Event> updateEvent(@PathVariable String id, @RequestBody CreateEventRequest request) {
        return eventService.getEvent(id)
                .map(existing -> {
                    existing.setTitle(request.getTitle());
                    existing.setDescription(request.getDescription());
                    existing.setAuthor(request.getAuthor());
                    existing.setCategory(request.getCategory());
                    existing.setAvailableCopies(request.getAvailableCopies());
                    return ResponseEntity.ok(eventService.updateEvent(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable String id) {
        eventService.deleteEvent(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/views")
    public ResponseEntity<Long> getViewCount(@PathVariable String id) {
        return ResponseEntity.ok(eventService.getViewCount(id));
    }

    @PostMapping("/{id}/views")
    public ResponseEntity<Long> incrementViewCount(@PathVariable String id) {
        return ResponseEntity.ok(eventService.incrementViewCount(id));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<Event>> getEventsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(eventService.getEventsByCategory(category));
    }
}

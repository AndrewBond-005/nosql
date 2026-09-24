package ru.library.event.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.library.event.model.Event;
import ru.library.event.repository.KeyValueRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    private static final String EVENT_PREFIX = "event:";
    private static final String VIEW_COUNTER_PREFIX = "views:";

    private final KeyValueRepository<Event> eventRepository;

    @Autowired
    public EventService(KeyValueRepository<Event> eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event createEvent(Event event) {
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.put(EVENT_PREFIX + event.getId(), event);
        return event;
    }

    @Cacheable(value = "events", key = "#id")
    public Optional<Event> getEvent(String id) {
        return eventRepository.get(EVENT_PREFIX + id);
    }

    public List<Event> getAllEvents() {
        return eventRepository.getAll(EVENT_PREFIX);
    }

    @CachePut(value = "events", key = "#event.id")
    public Event updateEvent(Event event) {
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.put(EVENT_PREFIX + event.getId(), event);
        return event;
    }

    @CacheEvict(value = "events", key = "#id")
    public void deleteEvent(String id) {
        eventRepository.delete(EVENT_PREFIX + id);
    }

    public long incrementViewCount(String eventId) {
        return eventRepository.incrementCounter(VIEW_COUNTER_PREFIX + eventId, 1);
    }

    public long getViewCount(String eventId) {
        return eventRepository.getCounter(VIEW_COUNTER_PREFIX + eventId);
    }

    public List<Event> getEventsByCategory(String category) {
        return eventRepository.getAll(EVENT_PREFIX).stream()
                .filter(e -> e.getCategory().equals(category))
                .toList();
    }
}

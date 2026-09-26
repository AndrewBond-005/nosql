package ru.library.event.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.library.event.exception.EventNotFoundException;
import ru.library.event.model.Event;
import ru.library.event.repository.EventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Event createEvent(Event event) {
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        Event saved = eventRepository.save(event);
        log.info("Book created in PostgreSQL, cache events has no entry for the new key: eventId={}", saved.getId());
        return saved;
    }

    @Cacheable(value = "events", key = "#id")
    public Optional<Event> getEvent(String id) {
        return eventRepository.findById(id);
    }

    public List<Event> getAllEvents() {
        List<Event> events = eventRepository.findAll();
        log.info("Catalog read from PostgreSQL, the list is not cached: size={}", events.size());
        return events;
    }

    @CachePut(value = "events", key = "#event.id")
    public Event updateEvent(Event event) {
        event.setUpdatedAt(LocalDateTime.now());
        return eventRepository.save(event);
    }

    @CacheEvict(value = "events", key = "#id")
    public void deleteEvent(String id) {
        eventRepository.deleteById(id);
    }

    @CacheEvict(value = "events", key = "#eventId")
    @Transactional
    public long incrementViewCount(String eventId) {
        int updated = eventRepository.incrementViewCount(eventId, 1);
        if (updated == 0) {
            throw new EventNotFoundException(eventId);
        }
        return eventRepository.findById(eventId)
                .map(Event::getViewCount)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    public long getViewCount(String eventId) {
        return eventRepository.findById(eventId)
                .map(Event::getViewCount)
                .orElse(0L);
    }

    @CacheEvict(value = "events", key = "#eventId")
    @Transactional
    public int reserveOneCopy(String eventId) {
        int updated = eventRepository.reserveOneCopy(eventId);
        log.info("availableCopies decremented in PostgreSQL and evicted from cache events: eventId={}, updated={}",
                eventId, updated);
        return updated;
    }

    @CacheEvict(value = "events", key = "#eventId")
    @Transactional
    public int releaseOneCopy(String eventId) {
        int updated = eventRepository.releaseOneCopy(eventId);
        log.info("availableCopies incremented in PostgreSQL and evicted from cache events: eventId={}, updated={}",
                eventId, updated);
        return updated;
    }

    public List<Event> getEventsByCategory(String category) {
        return eventRepository.findByCategory(category);
    }
}

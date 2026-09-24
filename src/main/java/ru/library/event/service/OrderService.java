package ru.library.event.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.library.event.exception.EventNotFoundException;
import ru.library.event.exception.NoAvailableCopiesException;
import ru.library.event.exception.OrderNotFoundException;
import ru.library.event.model.Event;
import ru.library.event.model.Order;
import ru.library.event.model.TemporaryRequest;
import ru.library.event.repository.KeyValueRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String ORDER_PREFIX = "order:";
    private static final String TEMP_REQUEST_PREFIX = "temp:";
    private static final long DEFAULT_TEMP_TTL_SECONDS = 300; // 5 minutes

    private final KeyValueRepository<Order> orderRepository;
    private final KeyValueRepository<TemporaryRequest> tempRequestRepository;
    private final EventService eventService;

    // Per-event lock to prevent overselling when multiple threads order the same book.
    // Without this, two threads can both read availableCopies=1, both see it as available,
    // and both succeed — resulting in a negative count (lost update / overselling).
    private final ConcurrentHashMap<String, ReentrantLock> eventLocks = new ConcurrentHashMap<>();

    @Autowired
    public OrderService(KeyValueRepository<Order> orderRepository,
                        KeyValueRepository<TemporaryRequest> tempRequestRepository,
                        EventService eventService) {
        this.orderRepository = orderRepository;
        this.tempRequestRepository = tempRequestRepository;
        this.eventService = eventService;
    }

    public Order createOrder(String eventId, String managerId, String userId) {
        // Acquire per-event lock to make check-and-decrement atomic.
        ReentrantLock lock = eventLocks.computeIfAbsent(eventId, k -> new ReentrantLock());
        lock.lock();
        try {
            Optional<Event> eventOpt = eventService.getEvent(eventId);
            if (eventOpt.isEmpty()) {
                throw new EventNotFoundException(eventId);
            }

            Event event = eventOpt.get();
            if (event.getAvailableCopies() <= 0) {
                throw new NoAvailableCopiesException(eventId);
            }

            Order order = new Order(eventId, managerId, userId);
            orderRepository.put(ORDER_PREFIX + order.getId(), order);

            event.setAvailableCopies(event.getAvailableCopies() - 1);
            eventService.updateEvent(event);

            log.info("Order created: id={}, eventId={}, userId={}", order.getId(), eventId, userId);
            return order;
        } finally {
            lock.unlock();
        }
    }

    public Optional<Order> getOrder(String id) {
        return orderRepository.get(ORDER_PREFIX + id);
    }

    public List<Order> getAllOrders() {
        return orderRepository.getAll(ORDER_PREFIX);
    }

    public Order updateOrderStatus(String orderId, Order.Status status) {
        Optional<Order> orderOpt = orderRepository.get(ORDER_PREFIX + orderId);
        if (orderOpt.isEmpty()) {
            throw new OrderNotFoundException(orderId);
        }

        Order order = orderOpt.get();
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.put(ORDER_PREFIX + orderId, order);

        if (status == Order.Status.CANCELLED) {
            ReentrantLock lock = eventLocks.computeIfAbsent(order.getEventId(), k -> new ReentrantLock());
            lock.lock();
            try {
                Optional<Event> eventOpt = eventService.getEvent(order.getEventId());
                eventOpt.ifPresent(event -> {
                    event.setAvailableCopies(event.getAvailableCopies() + 1);
                    eventService.updateEvent(event);
                });
            } finally {
                lock.unlock();
            }
        }

        log.info("Order status updated: orderId={}, status={}", orderId, status);
        return order;
    }

    /**
     * Creates a temporary request with TTL-based expiration.
     *
     * In the Etcd profile, TTL is implemented via Etcd leases:
     * a lease is granted with the specified TTL, and the key is bound to it.
     * Etcd automatically deletes the key when the lease expires — no polling required.
     *
     * In the in-memory profile, TTL is tracked at the application level via expiresAt field.
     *
     * @param eventId  the event to reserve
     * @param userId   the user making the request
     * @param purpose  purpose of the reservation
     * @param ttlSeconds  time-to-live in seconds (0 = use default)
     */
    public TemporaryRequest createTemporaryRequest(String eventId, String userId, String purpose, long ttlSeconds) {
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TEMP_TTL_SECONDS;
        TemporaryRequest request = new TemporaryRequest(eventId, userId, purpose, effectiveTtl);

        // putWithTtl delegates to the repository:
        // - EtcdKeyValueRepository: creates an Etcd lease and binds the key
        // - InMemoryKeyValueRepository: sets application-level expiration
        tempRequestRepository.putWithTtl(TEMP_REQUEST_PREFIX + request.getId(), request, effectiveTtl);

        log.info("Temporary request created: id={}, eventId={}, userId={}, ttl={}s",
                request.getId(), eventId, userId, effectiveTtl);
        return request;
    }

    /**
     * Convenience overload with default TTL.
     */
    public TemporaryRequest createTemporaryRequest(String eventId, String userId, String purpose) {
        return createTemporaryRequest(eventId, userId, purpose, DEFAULT_TEMP_TTL_SECONDS);
    }

    public Optional<TemporaryRequest> getTemporaryRequest(String id) {
        return tempRequestRepository.get(TEMP_REQUEST_PREFIX + id);
    }

    public List<TemporaryRequest> getAllTemporaryRequests() {
        return tempRequestRepository.getAll(TEMP_REQUEST_PREFIX);
    }

    public void deleteTemporaryRequest(String id) {
        tempRequestRepository.delete(TEMP_REQUEST_PREFIX + id);
        log.info("Temporary request deleted: id={}", id);
    }
}

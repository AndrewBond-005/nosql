package ru.library.event.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.library.event.exception.EventNotFoundException;
import ru.library.event.exception.NoAvailableCopiesException;
import ru.library.event.exception.OrderNotFoundException;
import ru.library.event.model.Order;
import ru.library.event.model.TemporaryRequest;
import ru.library.event.repository.OrderRepository;
import ru.library.event.repository.TemporaryRequestRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final long DEFAULT_TEMP_TTL_SECONDS = 300;

    private final OrderRepository orderRepository;
    private final TemporaryRequestRepository tempRequestRepository;
    private final EventService eventService;

    public OrderService(OrderRepository orderRepository,
                        TemporaryRequestRepository tempRequestRepository,
                        EventService eventService) {
        this.orderRepository = orderRepository;
        this.tempRequestRepository = tempRequestRepository;
        this.eventService = eventService;
    }

    @Transactional
    public Order createOrder(String eventId, String userId) {
        int reserved = eventService.reserveOneCopy(eventId);
        if (reserved == 0) {
            if (eventService.getEvent(eventId).isEmpty()) {
                throw new EventNotFoundException(eventId);
            }
            throw new NoAvailableCopiesException(eventId);
        }

        Order order = orderRepository.save(new Order(eventId, userId, userId));
        log.info("Order created: id={}, eventId={}, userId={}, managerId={}",
                order.getId(), eventId, userId, userId);
        return order;
    }

    public Optional<Order> getOrder(String id) {
        return orderRepository.findById(id);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    @Transactional
    public Order updateOrderStatus(String orderId, Order.Status status) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (status == Order.Status.CANCELLED && order.getStatus() != Order.Status.CANCELLED) {
            eventService.releaseOneCopy(order.getEventId());
        }

        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());
        Order updated = orderRepository.save(order);
        log.info("Order status updated: orderId={}, status={}", orderId, status);
        return updated;
    }

    public TemporaryRequest createTemporaryRequest(String eventId, String userId, String purpose, long ttlSeconds) {
        long effectiveTtl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TEMP_TTL_SECONDS;
        TemporaryRequest request = new TemporaryRequest(eventId, userId, purpose, effectiveTtl);
        tempRequestRepository.saveWithTtl(request, effectiveTtl);
        log.info("Temporary request created: id={}, eventId={}, userId={}, ttl={}s",
                request.getId(), eventId, userId, effectiveTtl);
        return request;
    }

    public TemporaryRequest createTemporaryRequest(String eventId, String userId, String purpose) {
        return createTemporaryRequest(eventId, userId, purpose, DEFAULT_TEMP_TTL_SECONDS);
    }

    public Optional<TemporaryRequest> getTemporaryRequest(String id) {
        return tempRequestRepository.findById(id);
    }

    public List<TemporaryRequest> getAllTemporaryRequests() {
        return tempRequestRepository.findAll();
    }

    public void deleteTemporaryRequest(String id) {
        tempRequestRepository.deleteById(id);
        log.info("Temporary request deleted: id={}", id);
    }
}

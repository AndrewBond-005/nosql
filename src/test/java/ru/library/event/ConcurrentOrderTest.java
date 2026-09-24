package ru.library.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.library.event.model.Event;
import ru.library.event.model.Order;
import ru.library.event.service.EventService;
import ru.library.event.service.OrderService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates concurrent order creation race condition.
 *
 * Scenario: N threads simultaneously try to order a book with 1 available copy.
 * Expected result: exactly 1 order succeeds, the rest fail with NoAvailableCopiesException.
 *
 * Without transactions: multiple threads could read availableCopies=1 simultaneously,
 * all see it as available, and all succeed — overselling the book.
 *
 * With Etcd compare-and-swap (incrementCounter) and application-level checks:
 * the system prevents overselling because the copy count decrement is atomic.
 */
@SpringBootTest
@ActiveProfiles("inmemory")
class ConcurrentOrderTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private EventService eventService;

    @Test
    void concurrentOrdersShouldAllowExactlyOne() throws Exception {
        // Create an event with exactly 1 available copy
        Event event = new Event("Test Book", "Test", "Author", "Category", 1);
        Event created = eventService.createEvent(event);
        String eventId = created.getId();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Future<Order>> futures = new ArrayList<>();

        // Launch all threads simultaneously
        for (int i = 0; i < threadCount; i++) {
            final String userId = "user-" + i;
            futures.add(executor.submit(() -> {
                startLatch.await(); // All threads start at the same moment
                try {
                    Order order = orderService.createOrder(eventId, "manager-1", userId);
                    successCount.incrementAndGet();
                    return order;
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    return null;
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown(); // Release all threads
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify results
        System.out.println("=== Concurrent Order Test Results ===");
        System.out.println("Total threads: " + threadCount);
        System.out.println("Successful orders: " + successCount.get());
        System.out.println("Failed orders: " + failCount.get());

        // Exactly 1 order should succeed
        assertEquals(1, successCount.get(),
                "Exactly 1 order should succeed when 1 copy is available");
        assertEquals(threadCount - 1, failCount.get(),
                "All other orders should fail");

        // Verify the event now has 0 copies
        Event updatedEvent = eventService.getEvent(eventId).orElseThrow();
        assertEquals(0, updatedEvent.getAvailableCopies(),
                "Available copies should be 0 after ordering");
    }

    @Test
    void viewCounterShouldHandleConcurrentIncrements() throws Exception {
        Event event = new Event("Counter Test", "Test", "Author", "Category", 1);
        Event created = eventService.createEvent(event);
        String eventId = created.getId();

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    return eventService.incrementViewCount(eventId);
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long finalCount = eventService.getViewCount(eventId);
        System.out.println("=== Concurrent View Counter Test ===");
        System.out.println("Increments issued: " + threadCount);
        System.out.println("Final view count: " + finalCount);

        assertEquals(threadCount, finalCount,
                "All " + threadCount + " increments should be accounted for");
    }
}

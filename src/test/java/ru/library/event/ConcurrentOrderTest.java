package ru.library.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import ru.library.event.model.Event;
import ru.library.event.model.Order;
import ru.library.event.repository.UserSettingsRepository;
import ru.library.event.service.EventService;
import ru.library.event.service.OrderService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ConcurrentOrderTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> etcd = new GenericContainer<>(
            DockerImageName.parse("quay.io/coreos/etcd:v3.5.9"))
            .withExposedPorts(2379)
            .withCommand(
                    "/usr/local/bin/etcd",
                    "--name=etcd-node0",
                    "--data-dir=/etcd-data",
                    "--listen-client-urls=http://0.0.0.0:2379",
                    "--advertise-client-urls=http://127.0.0.1:2379",
                    "--listen-peer-urls=http://0.0.0.0:2380",
                    "--initial-advertise-peer-urls=http://127.0.0.1:2380",
                    "--initial-cluster=etcd-node0=http://127.0.0.1:2380",
                    "--initial-cluster-state=new"
            );

    @DynamicPropertySource
    static void configurePersistence(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("etcd.endpoints", () -> "http://" + etcd.getHost() + ":" + etcd.getMappedPort(2379));
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private EventService eventService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserSettingsRepository settingsRepository;

    @Test
    void concurrentOrdersShouldAllowExactlyOne() throws Exception {
        Event event = new Event("Test Book", "Test", "Author", "Category", 1);
        Event created = eventService.createEvent(event);
        String eventId = created.getId();
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        List<Future<Order>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                String userId = "user-" + i;
                futures.add(executor.submit(() -> {
                    startLatch.await();
                    try {
                        Order order = orderService.createOrder(eventId, userId);
                        successCount.incrementAndGet();
                        return order;
                    } catch (RuntimeException e) {
                        failCount.incrementAndGet();
                        return null;
                    } finally {
                        doneLatch.countDown();
                    }
                }));
            }

            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, successCount.get());
        assertEquals(threadCount - 1, failCount.get());
        assertEquals(0, eventService.getEvent(eventId).orElseThrow().getAvailableCopies());
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

        try {
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
        } finally {
            executor.shutdownNow();
        }

        assertEquals(threadCount, eventService.getViewCount(eventId));
    }

    @Test
    void httpBasicShouldProtectApisAndUserSettings() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"Auth-User\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("auth-user"));

        mockMvc.perform(post("/api/events")
                        .with(httpBasic("auth-user", "secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Security Test Event\",\"author\":\"Tester\",\"category\":\"Test\",\"availableCopies\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Security Test Event"))
                .andExpect(jsonPath("$.viewCount").value(0));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/.well-known/appspecific/com.chrome.devtools.json"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/settings/auth-user").with(httpBasic("auth-user", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("auth-user"));

        mockMvc.perform(get("/api/settings/another-user").with(httpBasic("auth-user", "secret123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"auth-user\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preferredThemeShouldBeStoredInProfileAndServedFromCache() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"theme-user\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/settings/theme-user/theme")
                        .with(httpBasic("theme-user", "secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("theme-user"))
                .andExpect(jsonPath("$.theme").value("DARK"));

        assertNotNull(cacheManager.getCache("userThemes").get("theme-user"),
                "Theme must be cached after it is saved");

        settingsRepository.deleteById("theme-user");

        mockMvc.perform(get("/api/settings/theme-user/theme").with(httpBasic("theme-user", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("DARK"));

        mockMvc.perform(put("/api/settings/another-user/theme")
                        .with(httpBasic("theme-user", "secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"DARK\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnShouldReleaseCopyAndProtectAgainstDoubleReturn() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"return-user\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"other-user\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());

        Event created = eventService.createEvent(new Event("Return Test Book", "Test", "Author", "Category", 1));
        String eventId = created.getId();

        MvcResult createdOrder = mockMvc.perform(post("/api/orders")
                        .with(httpBasic("return-user", "secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + eventId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String orderId = readJson(createdOrder, "id");
        assertEquals(0, eventService.getEvent(eventId).orElseThrow().getAvailableCopies());

        mockMvc.perform(post("/api/orders/" + orderId + "/return")
                        .with(httpBasic("return-user", "secret123")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/orders/" + orderId + "/return")
                        .with(httpBasic("other-user", "secret123")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/orders/" + orderId + "/return")
                        .with(httpBasic("return-user", "secret123")))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(put("/api/orders/" + orderId + "/status?status=COMPLETED")
                        .with(httpBasic("return-user", "secret123")))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/orders/" + orderId + "/status?status=CONFIRMED")
                        .with(httpBasic("return-user", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/orders/" + orderId + "/return")
                        .with(httpBasic("return-user", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.returnedAt").isNotEmpty());

        assertEquals(1, eventService.getEvent(eventId).orElseThrow().getAvailableCopies());

        mockMvc.perform(post("/api/orders/" + orderId + "/return")
                        .with(httpBasic("return-user", "secret123")))
                .andExpect(status().isConflict());

        assertEquals(1, eventService.getEvent(eventId).orElseThrow().getAvailableCopies());
    }

    private static String readJson(MvcResult result, String field) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.getResponse().getContentAsString())
                .get(field)
                .asText();
    }

    @Test
    void orderForBookWithoutCopiesShouldBeRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"nocopy-user\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());

        Event created = eventService.createEvent(new Event("No Copies Book", "Test", "Author", "Category", 0));

        mockMvc.perform(post("/api/orders")
                        .with(httpBasic("nocopy-user", "secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + created.getId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Копий книги больше нет: " + created.getId()));
    }

    @Test
    void orderShouldUseAuthenticatedUserAsManager() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"order-user\",\"password\":\"secret123\"}"))
                .andExpect(status().isCreated());

        Event created = eventService.createEvent(
                new Event("Order Test Book", "Test", "Author", "Category", 2));

        mockMvc.perform(post("/api/orders")
                        .with(httpBasic("order-user", "secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + created.getId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("order-user"))
                .andExpect(jsonPath("$.managerId").value("order-user"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}

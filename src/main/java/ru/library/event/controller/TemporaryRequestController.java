package ru.library.event.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.library.event.model.TemporaryRequest;
import ru.library.event.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/temp-requests")
@CrossOrigin(origins = "*")
public class TemporaryRequestController {

    private final OrderService orderService;

    @Autowired
    public TemporaryRequestController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<TemporaryRequest> createTemporaryRequest(
            @RequestParam String eventId,
            @RequestParam String userId,
            @RequestParam(defaultValue = "reservation") String purpose,
            @RequestParam(defaultValue = "300") long ttlSeconds) {
        TemporaryRequest request = orderService.createTemporaryRequest(eventId, userId, purpose, ttlSeconds);
        return ResponseEntity.ok(request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemporaryRequest> getTemporaryRequest(@PathVariable String id) {
        return orderService.getTemporaryRequest(id)
                .filter(req -> !req.isExpired())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<TemporaryRequest>> getAllTemporaryRequests() {
        List<TemporaryRequest> requests = orderService.getAllTemporaryRequests().stream()
                .filter(req -> !req.isExpired())
                .toList();
        return ResponseEntity.ok(requests);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemporaryRequest(@PathVariable String id) {
        orderService.deleteTemporaryRequest(id);
        return ResponseEntity.ok().build();
    }
}

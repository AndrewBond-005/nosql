package ru.library.event.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Order {
    public enum Status {
        PENDING, CONFIRMED, COMPLETED, CANCELLED
    }

    private String id;
    private String eventId;
    private String managerId;
    private String userId;
    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Order() {
        this.id = UUID.randomUUID().toString();
        this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Order(String eventId, String managerId, String userId) {
        this();
        this.eventId = eventId;
        this.managerId = managerId;
        this.userId = userId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

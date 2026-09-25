package ru.library.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {
    public enum Status {
        PENDING, CONFIRMED, COMPLETED, CANCELLED
    }

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(length = 36, nullable = false)
    private String eventId;

    @Column(length = 36, nullable = false)
    private String managerId;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime returnedAt;

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

    public LocalDateTime getReturnedAt() { return returnedAt; }
    public void setReturnedAt(LocalDateTime returnedAt) { this.returnedAt = returnedAt; }
}

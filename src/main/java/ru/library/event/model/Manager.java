package ru.library.event.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Manager {
    private String id;
    private String name;
    private String email;
    private boolean active;
    private LocalDateTime createdAt;

    public Manager() {
        this.id = UUID.randomUUID().toString();
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public Manager(String name, String email) {
        this();
        this.name = name;
        this.email = email;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

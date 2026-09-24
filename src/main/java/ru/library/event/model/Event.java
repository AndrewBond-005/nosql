package ru.library.event.model;

import java.time.LocalDateTime;
import java.util.UUID;

public class Event {
    private String id;
    private String title;
    private String description;
    private String author;
    private String category;
    private int availableCopies;
    private long viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Event() {
        this.id = UUID.randomUUID().toString();
        this.viewCount = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Event(String title, String description, String author, String category, int availableCopies) {
        this();
        this.title = title;
        this.description = description;
        this.author = author;
        this.category = category;
        this.availableCopies = availableCopies;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getAvailableCopies() { return availableCopies; }
    public void setAvailableCopies(int availableCopies) { this.availableCopies = availableCopies; }

    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

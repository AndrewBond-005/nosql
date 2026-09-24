package ru.library.event.model;

public class CreateOrderRequest {
    private String eventId;
    private String userId;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}

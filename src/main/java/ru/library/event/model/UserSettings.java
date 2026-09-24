package ru.library.event.model;

import java.time.LocalDateTime;

public class UserSettings {
    private String userId;
    private String preferredCategory;
    private String language;
    private boolean notificationsEnabled;
    private int maxResultsPerPage;
    private LocalDateTime lastUpdated;

    public UserSettings() {
        this.notificationsEnabled = true;
        this.maxResultsPerPage = 20;
        this.lastUpdated = LocalDateTime.now();
    }

    public UserSettings(String userId) {
        this();
        this.userId = userId;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPreferredCategory() { return preferredCategory; }
    public void setPreferredCategory(String preferredCategory) { this.preferredCategory = preferredCategory; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }

    public int getMaxResultsPerPage() { return maxResultsPerPage; }
    public void setMaxResultsPerPage(int maxResultsPerPage) { this.maxResultsPerPage = maxResultsPerPage; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}

package ru.library.event.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_settings")
public class UserSettings {
    public enum Theme {
        LIGHT,
        DARK
    }

    @Id
    @Column(length = 255, nullable = false, updatable = false)
    private String userId;

    private String preferredCategory;
    private String language;

    @Column(nullable = false)
    private boolean notificationsEnabled;

    @Column(nullable = false)
    private int maxResultsPerPage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, columnDefinition = "varchar(16) not null default 'LIGHT'")
    private Theme theme = Theme.LIGHT;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    public UserSettings() {
        this.notificationsEnabled = true;
        this.maxResultsPerPage = 20;
        this.theme = Theme.LIGHT;
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

    public Theme getTheme() { return theme; }
    public void setTheme(Theme theme) { this.theme = theme == null ? Theme.LIGHT : theme; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}

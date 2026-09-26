package ru.library.event.model;

public class ThemeResponse {
    private final String userId;
    private final UserSettings.Theme theme;

    public ThemeResponse(String userId, UserSettings.Theme theme) {
        this.userId = userId;
        this.theme = theme;
    }

    public String getUserId() { return userId; }
    public UserSettings.Theme getTheme() { return theme; }
}

package ru.library.event.model;

public class ThemeRequest {
    private UserSettings.Theme theme = UserSettings.Theme.LIGHT;

    public UserSettings.Theme getTheme() { return theme; }
    public void setTheme(UserSettings.Theme theme) { this.theme = theme; }
}

package ru.library.event.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.library.event.model.ThemeRequest;
import ru.library.event.model.ThemeResponse;
import ru.library.event.model.UserSettings;
import ru.library.event.service.UserSettingsService;

@RestController
@RequestMapping("/api/settings")
@CrossOrigin(origins = "*")
public class UserSettingsController {
    private final UserSettingsService userSettingsService;

    @Autowired
    public UserSettingsController(UserSettingsService userSettingsService) {
        this.userSettingsService = userSettingsService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserSettings> getSettings(@PathVariable String userId, Authentication authentication) {
        requireCurrentUser(userId, authentication);
        return ResponseEntity.ok(userSettingsService.getOrCreateDefault(authentication.getName()));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserSettings> updateSettings(@PathVariable String userId,
                                                       @RequestBody UserSettings settings,
                                                       Authentication authentication) {
        requireCurrentUser(userId, authentication);
        settings.setUserId(authentication.getName());
        return ResponseEntity.ok(userSettingsService.saveSettings(settings));
    }

    @GetMapping("/{userId}/theme")
    public ResponseEntity<ThemeResponse> getTheme(@PathVariable String userId, Authentication authentication) {
        requireCurrentUser(userId, authentication);
        String currentUserId = authentication.getName();
        return ResponseEntity.ok(new ThemeResponse(currentUserId, userSettingsService.getTheme(currentUserId)));
    }

    @PutMapping("/{userId}/theme")
    public ResponseEntity<ThemeResponse> updateTheme(@PathVariable String userId,
                                                     @RequestBody ThemeRequest request,
                                                     Authentication authentication) {
        requireCurrentUser(userId, authentication);
        String currentUserId = authentication.getName();
        UserSettings.Theme theme = userSettingsService.setTheme(currentUserId, request.getTheme());
        return ResponseEntity.ok(new ThemeResponse(currentUserId, theme));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteSettings(@PathVariable String userId, Authentication authentication) {
        requireCurrentUser(userId, authentication);
        userSettingsService.deleteSettings(authentication.getName());
        return ResponseEntity.ok().build();
    }

    private static void requireCurrentUser(String userId, Authentication authentication) {
        if (!authentication.getName().equals(userId)) {
            throw new AccessDeniedException("Settings belong to another user");
        }
    }
}

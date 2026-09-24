package ru.library.event.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    public ResponseEntity<UserSettings> getSettings(@PathVariable String userId) {
        return ResponseEntity.ok(userSettingsService.getOrCreateDefault(userId));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserSettings> updateSettings(@PathVariable String userId,
                                                       @RequestBody UserSettings settings) {
        settings.setUserId(userId);
        return ResponseEntity.ok(userSettingsService.saveSettings(settings));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteSettings(@PathVariable String userId) {
        userSettingsService.deleteSettings(userId);
        return ResponseEntity.ok().build();
    }
}

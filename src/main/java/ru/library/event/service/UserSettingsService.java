package ru.library.event.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.library.event.model.UserSettings;
import ru.library.event.repository.KeyValueRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserSettingsService {

    private static final String SETTINGS_PREFIX = "settings:";

    private final KeyValueRepository<UserSettings> settingsRepository;

    @Autowired
    public UserSettingsService(KeyValueRepository<UserSettings> settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    @CachePut(value = "userSettings", key = "#settings.userId")
    public UserSettings saveSettings(UserSettings settings) {
        settings.setLastUpdated(LocalDateTime.now());
        settingsRepository.put(SETTINGS_PREFIX + settings.getUserId(), settings);
        return settings;
    }

    @Cacheable(value = "userSettings", key = "#userId")
    public Optional<UserSettings> getSettings(String userId) {
        return settingsRepository.get(SETTINGS_PREFIX + userId);
    }

    @CacheEvict(value = "userSettings", key = "#userId")
    public void deleteSettings(String userId) {
        settingsRepository.delete(SETTINGS_PREFIX + userId);
    }

    public UserSettings getOrCreateDefault(String userId) {
        return getSettings(userId).orElseGet(() -> {
            UserSettings defaultSettings = new UserSettings(userId);
            return saveSettings(defaultSettings);
        });
    }
}

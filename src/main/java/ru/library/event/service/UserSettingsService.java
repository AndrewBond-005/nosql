package ru.library.event.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.library.event.model.UserSettings;
import ru.library.event.repository.UserSettingsRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserSettingsService {
    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    private final UserSettingsRepository settingsRepository;
    private final CacheManager cacheManager;

    public UserSettingsService(UserSettingsRepository settingsRepository, CacheManager cacheManager) {
        this.settingsRepository = settingsRepository;
        this.cacheManager = cacheManager;
    }

    @CachePut(value = "userSettings", key = "#settings.userId")
    public UserSettings saveSettings(UserSettings settings) {
        settings.setLastUpdated(LocalDateTime.now());
        UserSettings saved = settingsRepository.save(settings);
        evict("userThemes", saved.getUserId());
        return saved;
    }

    @Cacheable(value = "userSettings", key = "#userId")
    public Optional<UserSettings> getSettings(String userId) {
        return settingsRepository.findById(userId);
    }

    @Cacheable(value = "userThemes", key = "#userId")
    public UserSettings.Theme getTheme(String userId) {
        UserSettings.Theme theme = getOrCreateDefault(userId).getTheme();
        log.info("Theme value read from PostgreSQL and stored into cache userThemes: userId={}, theme={}", userId, theme);
        return theme;
    }

    @CachePut(value = "userThemes", key = "#userId")
    public UserSettings.Theme setTheme(String userId, UserSettings.Theme theme) {
        UserSettings settings = getOrCreateDefault(userId);
        settings.setTheme(theme);
        settingsRepository.save(settings);
        evict("userSettings", userId);
        log.info("Theme saved in PostgreSQL and put into cache userThemes: userId={}, theme={}", userId, settings.getTheme());
        return settings.getTheme();
    }

    @CacheEvict(value = "userSettings", key = "#userId")
    public void deleteSettings(String userId) {
        settingsRepository.deleteById(userId);
        evict("userThemes", userId);
    }

    public UserSettings getOrCreateDefault(String userId) {
        return getSettings(userId).orElseGet(() -> saveSettings(new UserSettings(userId)));
    }

    private void evict(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }
}

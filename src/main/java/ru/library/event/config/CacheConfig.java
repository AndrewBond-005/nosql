package ru.library.event.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class CacheConfig {

    public static final List<String> CACHE_NAMES = List.of("events", "userSettings", "userThemes");

    @Bean
    public CacheManager cacheManager(@Value("${app.cache-logging:true}") boolean cacheLogging) {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(CACHE_NAMES.stream()
                .map(name -> (org.springframework.cache.Cache) new LoggingCache(name, cacheLogging))
                .toList());
        return cacheManager;
    }
}

package ru.library.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.library.event.model.UserSettings;

public interface UserSettingsRepository extends JpaRepository<UserSettings, String> {
}

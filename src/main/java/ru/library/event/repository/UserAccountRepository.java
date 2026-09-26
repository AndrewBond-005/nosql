package ru.library.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.library.event.model.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
}

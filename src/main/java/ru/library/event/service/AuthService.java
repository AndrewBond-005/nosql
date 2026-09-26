package ru.library.event.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.library.event.exception.UserAlreadyExistsException;
import ru.library.event.model.AuthRequest;
import ru.library.event.model.AuthResponse;
import ru.library.event.model.UserAccount;
import ru.library.event.repository.UserAccountRepository;

import java.util.Locale;

@Service
public class AuthService {
    private final UserAccountRepository userAccountRepository;
    private final UserSettingsService userSettingsService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserAccountRepository userAccountRepository,
                       UserSettingsService userSettingsService,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userAccountRepository = userAccountRepository;
        this.userSettingsService = userSettingsService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        String userId = normalizeUserId(request.getUserId());
        if (userAccountRepository.existsById(userId)) {
            throw new UserAlreadyExistsException(userId);
        }

        userAccountRepository.save(new UserAccount(userId, passwordEncoder.encode(request.getPassword())));
        userSettingsService.getOrCreateDefault(userId);
        return new AuthResponse(userId);
    }

    public AuthResponse login(AuthRequest request) {
        String userId = normalizeUserId(request.getUserId());
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(userId, request.getPassword())
        );
        return new AuthResponse(userId);
    }

    private static String normalizeUserId(String userId) {
        return userId.trim().toLowerCase(Locale.ROOT);
    }
}

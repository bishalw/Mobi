package com.scalablerest.auth.config;

import com.scalablerest.auth.entity.User;
import com.scalablerest.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminUserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserBootstrap.class);

    private final boolean enabled;
    private final String username;
    private final String password;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserBootstrap(@Value("${admin.bootstrap.enabled:false}") boolean enabled,
                              @Value("${admin.bootstrap.username:admin}") String username,
                              @Value("${admin.bootstrap.password:}") String password,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder) {
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("admin.bootstrap.password must be set when admin bootstrap is enabled");
        }

        User admin = userRepository.findByUsername(username)
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername(username);
                    log.info("Creating bootstrap admin user '{}'", username);
                    return user;
                });

        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole("ADMIN");
        userRepository.save(admin);
    }
}

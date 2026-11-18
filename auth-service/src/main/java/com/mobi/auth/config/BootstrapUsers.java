package com.mobi.auth.config;

import com.mobi.auth.entity.User;
import com.mobi.auth.event.UserEventPublisher;
import com.mobi.auth.event.UserEventType;
import com.mobi.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Creates the demo staff accounts (admin, warehouse, supplier) at startup, so a fresh
 * environment can be signed into without a manual setup step.
 *
 * <p>Each enabled account is created if missing and its password, role and supplier link
 * are reset on every start, so the values in the environment are always the truth.
 * Cloud environments turn every account off: there, a restart must never reset a real
 * person's password.
 *
 * <p>Then every staff account (admin, warehouse, supplier), bootstrapped or not, is
 * republished on {@code user-events} as a snapshot. Services that project users
 * (notification-service emails admins and warehouse staff by role) then always know who
 * the staff are, including accounts that never registered and, in the cloud where no
 * accounts are bootstrapped, real staff from before roles were on the event.
 */
@Component
public class BootstrapUsers implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapUsers.class);
    static final List<String> STAFF_ROLES = List.of("ADMIN", "WAREHOUSE", "SUPPLIER");

    private final Accounts accounts;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserEventPublisher userEventPublisher;

    public BootstrapUsers(Accounts accounts, UserRepository userRepository, PasswordEncoder passwordEncoder,
                          UserEventPublisher userEventPublisher) {
        this.accounts = accounts;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userEventPublisher = userEventPublisher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        accounts.users().forEach(this::apply);
        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        staff.forEach(user -> userEventPublisher.publish(UserEventType.USER_SNAPSHOT, user));
        log.info("Published snapshots of {} staff accounts", staff.size());
    }

    void apply(String key, Account account) {
        if (!account.enabled()) {
            return;
        }
        if (account.password() == null || account.password().isBlank()) {
            throw new IllegalStateException("bootstrap.users." + key + ".password must be set when that account is enabled");
        }
        if (account.username() == null || account.username().isBlank() || account.role() == null) {
            throw new IllegalStateException("bootstrap.users." + key + " needs a username and role");
        }

        User user = userRepository.findByUsername(account.username())
                .orElseGet(() -> {
                    User created = new User();
                    created.setUsername(account.username());
                    log.info("Creating bootstrap {} user '{}'", account.role(), account.username());
                    return created;
                });

        user.setPassword(passwordEncoder.encode(account.password()));
        user.assignRole(account.role(), account.supplierId());
        if (account.email() != null && !account.email().isBlank()) {
            String email = account.email().trim();
            // Emails are unique. If someone else already registered this one, keep the
            // account without it rather than fail startup.
            boolean takenByOther = userRepository.findByEmail(email)
                    .filter(other -> !other.getUsername().equals(account.username())).isPresent();
            if (takenByOther) {
                log.warn("Bootstrap {} email {} belongs to another account; leaving it unset", key, email);
            } else {
                user.setEmail(email);
            }
        }
        userRepository.save(user);
    }

    /** One entry per account under {@code bootstrap.users.<key>}. */
    @ConfigurationProperties("bootstrap")
    public record Accounts(Map<String, Account> users) {
        public Accounts {
            users = users == null ? Map.of() : users;
        }
    }

    public record Account(boolean enabled, String username, String password, String role, Long supplierId,
                          String email) {
    }
}

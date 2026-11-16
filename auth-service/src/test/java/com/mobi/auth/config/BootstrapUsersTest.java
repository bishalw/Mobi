package com.mobi.auth.config;

import com.mobi.auth.config.BootstrapUsers.Account;
import com.mobi.auth.config.BootstrapUsers.Accounts;
import com.mobi.auth.entity.User;
import com.mobi.auth.event.UserEventPublisher;
import com.mobi.auth.event.UserEventType;
import com.mobi.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapUsersTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final Map<String, User> saved = new HashMap<>();

    BootstrapUsersTest() {
        when(repository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            saved.put(user.getUsername(), user);
            return user;
        });
    }

    private final UserEventPublisher publisher = org.mockito.Mockito.mock(UserEventPublisher.class);

    @Test
    void publishesEachAccountWithItsEmailSoOtherServicesCanReachIt() {
        User existingAdmin = new User("ops-lead", "x", "ADMIN");
        when(repository.findByRoleIn(BootstrapUsers.STAFF_ROLES)).thenAnswer(inv -> {
            List<User> staff = new java.util.ArrayList<>(saved.values());
            staff.add(existingAdmin);
            return staff;
        });
        new BootstrapUsers(new Accounts(Map.of("warehouse",
                new Account(true, "warehouse", "pass", "WAREHOUSE", null, "warehouse@mobi.local"))),
                repository, NoOpPasswordEncoder.getInstance(), publisher).run(null);

        assertThat(saved.get("warehouse").getEmail()).isEqualTo("warehouse@mobi.local");
        // Bootstrapped and pre-existing staff alike, so role-based email has recipients.
        verify(publisher).publish(UserEventType.USER_SNAPSHOT, saved.get("warehouse"));
        verify(publisher).publish(UserEventType.USER_SNAPSHOT, existingAdmin);
    }

    @Test
    void createsEveryEnabledAccountFromProperties() {
        Map<String, String> props = new HashMap<>();
        props.put("bootstrap.users.admin.enabled", "true");
        props.put("bootstrap.users.admin.username", "admin");
        props.put("bootstrap.users.admin.password", "adminpass");
        props.put("bootstrap.users.admin.role", "ADMIN");
        props.put("bootstrap.users.warehouse.enabled", "true");
        props.put("bootstrap.users.warehouse.username", "warehouse");
        props.put("bootstrap.users.warehouse.password", "warehousepass");
        props.put("bootstrap.users.warehouse.role", "WAREHOUSE");
        props.put("bootstrap.users.supplier.enabled", "true");
        props.put("bootstrap.users.supplier.username", "supplier");
        props.put("bootstrap.users.supplier.password", "supplierpass");
        props.put("bootstrap.users.supplier.role", "SUPPLIER");
        props.put("bootstrap.users.supplier.supplier-id", "1");

        new BootstrapUsers(bind(props), repository, NoOpPasswordEncoder.getInstance(), publisher).run(null);

        assertThat(saved).containsOnlyKeys("admin", "warehouse", "supplier");
        assertThat(saved.get("warehouse").getRole()).isEqualTo("WAREHOUSE");
        assertThat(saved.get("supplier").getSupplierId()).isEqualTo(1L);
        assertThat(saved.get("admin").getSupplierId()).isNull();
        assertThat(saved.get("supplier").getPassword()).isEqualTo("supplierpass");
    }

    @Test
    void skipsDisabledAccounts() {
        new BootstrapUsers(new Accounts(Map.of("warehouse", new Account(false, "warehouse", null, "WAREHOUSE", null, null))),
                repository, NoOpPasswordEncoder.getInstance(), publisher).run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void resetsAnExistingAccount() {
        User existing = new User("warehouse", "old", "USER");
        when(repository.findByUsername("warehouse")).thenReturn(Optional.of(existing));

        new BootstrapUsers(new Accounts(Map.of("warehouse", new Account(true, "warehouse", "new", "WAREHOUSE", null, null))),
                repository, NoOpPasswordEncoder.getInstance(), publisher).run(null);

        assertThat(existing.getRole()).isEqualTo("WAREHOUSE");
        assertThat(existing.getPassword()).isEqualTo("new");
    }

    @Test
    void enabledAccountWithoutPasswordFailsStartup() {
        BootstrapUsers bootstrap = new BootstrapUsers(
                new Accounts(Map.of("supplier", new Account(true, "supplier", "", "SUPPLIER", 1L, null))),
                repository, NoOpPasswordEncoder.getInstance(), publisher);

        assertThatThrownBy(() -> bootstrap.run(null)).isInstanceOf(IllegalStateException.class);
    }

    private static Accounts bind(Map<String, String> properties) {
        return new Binder(List.of(new MapConfigurationPropertySource(properties)))
                .bind("bootstrap", Accounts.class).get();
    }
}

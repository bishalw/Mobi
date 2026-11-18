package com.mobi.auth.service;

import com.mobi.auth.dto.UserSummaryResponse;
import com.mobi.auth.entity.User;
import com.mobi.auth.event.UserEventPublisher;
import com.mobi.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private UserRepository userRepository;
    private AdminUserService service;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenService = mock(RefreshTokenService.class);
        service = new AdminUserService(userRepository, mock(UserEventPublisher.class), refreshTokenService);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void looksUpSeveralUsersInOneQueryIgnoringDuplicates() {
        when(userRepository.findAllById(List.of(7L, 9L))).thenReturn(List.of(new User("alice", "h", "USER")));

        List<UserSummaryResponse> users = service.getUsersByIds(List.of(7L, 9L, 7L));

        assertThat(users).extracting(UserSummaryResponse::getUsername).containsExactly("alice");
        verify(userRepository).findAllById(List.of(7L, 9L));
    }

    @Test
    void refusesOversizedLookups() {
        List<Long> tooMany = LongStream.rangeClosed(1, AdminUserService.MAX_LOOKUP_IDS + 1).boxed().toList();

        assertThatThrownBy(() -> service.getUsersByIds(tooMany)).isInstanceOf(com.mobi.auth.exception.BadRequestException.class);
    }

    @Test
    void unlockClearsTheLockout() {
        User user = new User("bob", "h", "USER");
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(5, Duration.ofMinutes(15), LocalDateTime.now());
        }
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        UserSummaryResponse response = service.unlock(3L);

        assertThat(response.getLockedUntil()).isNull();
        assertThat(user.isLocked(LocalDateTime.now())).isFalse();
    }

    @Test
    void supplierRoleLinksTheSupplierAndEndsSessions() {
        User user = new User("acme", "h", "USER");
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        UserSummaryResponse response = service.updateRole(4L, "supplier", 1L);

        assertThat(response.getRole()).isEqualTo("SUPPLIER");
        assertThat(response.getSupplierId()).isEqualTo(1L);
        verify(refreshTokenService).revokeAllForUser(4L);
    }

    @Test
    void supplierRoleWithoutSupplierIsRejected() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(new User("acme", "h", "USER")));

        assertThatThrownBy(() -> service.updateRole(4L, "SUPPLIER", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void leavingSupplierRoleClearsTheLink() {
        User user = new User("acme", "h", "USER");
        user.assignRole("SUPPLIER", 1L);
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        UserSummaryResponse response = service.updateRole(4L, "WAREHOUSE", 1L);

        assertThat(response.getRole()).isEqualTo("WAREHOUSE");
        assertThat(response.getSupplierId()).isNull();
    }
}

package com.mobi.auth.entity;

import com.mobi.auth.dto.UserSummaryResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserLockoutTest {

    @Test
    void lockedAccountIsReportedAndAdminUnlockClearsIt() {
        User user = new User("alice", "hash", "USER");
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(5, Duration.ofMinutes(15), now);
        }

        assertThat(user.isLocked(now)).isTrue();
        assertThat(UserSummaryResponse.from(user).getLockedUntil()).isNotNull();

        user.unlock();

        assertThat(user.isLocked(LocalDateTime.now())).isFalse();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(UserSummaryResponse.from(user).getLockedUntil()).isNull();
    }

    @Test
    void expiredLockIsNotReportedAsLocked() {
        User user = new User("bob", "hash", "USER");
        LocalDateTime longAgo = LocalDateTime.now().minusHours(1);
        for (int i = 0; i < 5; i++) {
            user.recordFailedLogin(5, Duration.ofMinutes(15), longAgo);
        }

        assertThat(UserSummaryResponse.from(user).getLockedUntil()).isNull();
    }
}

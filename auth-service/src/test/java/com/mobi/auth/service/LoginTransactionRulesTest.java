package com.mobi.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mobi.auth.dto.LoginRequest;
import com.mobi.auth.exception.AccountLockedException;
import com.mobi.auth.exception.InvalidCredentialsException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

// Unit tests mock the repository, so they cannot see a rollback. If a wrong-password
// exception rolls the transaction back, the failed-attempt counter is never stored and
// accounts never lock. This pins the rule that keeps it committed.
class LoginTransactionRulesTest {

    @Test
    void failedLoginExceptionsDoNotRollBackTheAttemptCounter() throws Exception {
        Transactional tx = AuthServiceImpl.class
                .getMethod("login", LoginRequest.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(List.of(tx.noRollbackFor()))
                .contains(InvalidCredentialsException.class, AccountLockedException.class);
    }
}

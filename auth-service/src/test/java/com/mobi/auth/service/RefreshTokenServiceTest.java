package com.mobi.auth.service;

import com.mobi.auth.entity.RefreshToken;
import com.mobi.auth.exception.InvalidRefreshTokenException;
import com.mobi.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private final Map<String, RefreshToken> byHash = new HashMap<>();
    private RefreshTokenRepository repository;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshTokenRepository.class);
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            byHash.put(token.getTokenHash(), token);
            return token;
        });
        when(repository.findByTokenHash(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(byHash.get(invocation.<String>getArgument(0))));
        service = new RefreshTokenService(repository, 14);
    }

    @Test
    void tokensAreStoredOnlyAsHashes() {
        String raw = service.issue(7L, "agent");

        assertEquals(1, byHash.size());
        assertNotEquals(raw, byHash.keySet().iterator().next());
        assertEquals(64, byHash.keySet().iterator().next().length());
    }

    @Test
    void rotationReturnsNewTokenInSameFamily() {
        String first = service.issue(7L, "agent");

        RefreshTokenService.Rotation rotation = service.rotate(first, "agent");

        assertEquals(7L, rotation.userId());
        assertNotEquals(first, rotation.token());
        RefreshToken original = byHash.get(RefreshTokenService.hash(first));
        RefreshToken successor = byHash.get(RefreshTokenService.hash(rotation.token()));
        assertEquals(original.getFamilyId(), successor.getFamilyId());
        verify(repository, never()).revokeFamily(anyString(), any());
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() {
        String first = service.issue(7L, "agent");
        service.rotate(first, "agent");
        String family = byHash.get(RefreshTokenService.hash(first)).getFamilyId();

        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(first, "attacker"));

        verify(repository).revokeFamily(eq(family), any());
    }

    @Test
    void unknownOrMissingTokenIsRejected() {
        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate("nope", null));
        assertThrows(InvalidRefreshTokenException.class, () -> service.rotate(null, null));
    }
}

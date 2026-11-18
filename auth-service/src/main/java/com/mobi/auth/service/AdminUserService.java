package com.mobi.auth.service;

import com.mobi.auth.dto.PageResponse;
import com.mobi.auth.dto.UserSummaryResponse;
import com.mobi.auth.entity.User;
import com.mobi.auth.event.UserEventPublisher;
import com.mobi.auth.event.UserEventType;
import com.mobi.auth.exception.UserNotFoundException;
import com.mobi.auth.repository.UserRepository;
import com.mobi.auth.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import java.util.Set;

@Service
public class AdminUserService {

    // The set of roles an administrator may assign. Free-text roles would be accepted by
    // the column and then silently fail every authority check, producing an account that
    // looks privileged in the directory and is not.
    private static final Set<String> ASSIGNABLE_ROLES = Set.of("USER", "ADMIN", "WAREHOUSE", "SUPPLIER");

    private final UserRepository userRepository;
    private final UserEventPublisher userEventPublisher;

    private final RefreshTokenService refreshTokenService;

    public AdminUserService(UserRepository userRepository, UserEventPublisher userEventPublisher,
                            RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
        this.userRepository = userRepository;
        this.userEventPublisher = userEventPublisher;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> listUsers(String search, Pageable pageable) {
        if (search == null || search.isBlank()) {
            return PageResponse.from(userRepository.findAll(pageable), UserSummaryResponse::from);
        }
        return PageResponse.from(
                userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        search.trim(), search.trim(), pageable),
                UserSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse getUser(Long userId) {
        return UserSummaryResponse.from(findUser(userId));
    }

    @Transactional
    public UserSummaryResponse updateRole(Long userId, String role) {
        return updateRole(userId, role, null);
    }

    @Transactional
    public UserSummaryResponse updateRole(Long userId, String role, Long supplierId) {
        String normalised = role == null ? "" : role.trim().toUpperCase();
        if (!ASSIGNABLE_ROLES.contains(normalised)) {
            throw new IllegalArgumentException("Role must be one of " + ASSIGNABLE_ROLES);
        }
        if ("SUPPLIER".equals(normalised) && supplierId == null) {
            throw new IllegalArgumentException("supplierId is required for the SUPPLIER role");
        }

        User user = findUser(userId);
        // Without this an administrator can demote themselves, and if they are the only
        // administrator the role endpoint becomes unreachable for everyone — including
        // the person who would need to undo it.
        requireNotSelf(userId, "You cannot change your own role");

        user.assignRole(normalised, supplierId);
        // Sessions carry the old role and supplier in their tokens; end them so the
        // change applies at the next login rather than when the refresh token expires.
        refreshTokenService.revokeAllForUser(userId);
        User saved = userRepository.save(user);
        userEventPublisher.publish(UserEventType.USER_ROLE_CHANGED, saved);
        return UserSummaryResponse.from(saved);
    }

    @Transactional
    public UserSummaryResponse updateStatus(Long userId, boolean enabled) {
        User user = findUser(userId);
        requireNotSelf(userId, "You cannot change your own account status");

        user.setEnabled(enabled);
        User saved = userRepository.save(user);
        if (!enabled) {
            // Suspension takes effect now, not when the account's sessions would have
            // expired: no refresh token survives, and the current access token lapses
            // within its 15 minutes.
            refreshTokenService.revokeAllForUser(userId);
        }
        userEventPublisher.publish(
                enabled ? UserEventType.USER_ENABLED : UserEventType.USER_DISABLED, saved);
        return UserSummaryResponse.from(saved);
    }

    /** At most one page's worth, so the call cannot be used to dump the whole table. */
    static final int MAX_LOOKUP_IDS = 100;

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getUsersByIds(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.size() > MAX_LOOKUP_IDS) {
            throw new com.mobi.auth.exception.BadRequestException("At most " + MAX_LOOKUP_IDS + " ids per request");
        }
        return userRepository.findAllById(distinct).stream().map(UserSummaryResponse::from).toList();
    }

    /** Lifts a password lockout early, e.g. after the owner has confirmed their identity. */
    @Transactional
    public UserSummaryResponse unlock(Long userId) {
        User user = findUser(userId);
        user.unlock();
        return UserSummaryResponse.from(userRepository.save(user));
    }

    // Republishes the current state of every account so a downstream projection can be
    // rebuilt. Needed because Kafka retention is finite: a consumer starting today cannot
    // read the registration events of accounts created before the retention window, and
    // those users would otherwise be permanently invisible to it.
    //
    // Walks the table a page at a time rather than with findAll() — a rebuild is exactly
    // the moment the user count is largest, and loading all of it to iterate once is how
    // an operational tool becomes an outage.
    @Transactional
    public long replayUserSnapshots() {
        int pageSize = 500;
        long published = 0;
        Page<User> page = userRepository.findAll(PageRequest.of(0, pageSize, Sort.by("id")));
        while (true) {
            for (User user : page.getContent()) {
                userEventPublisher.publish(UserEventType.USER_SNAPSHOT, user);
                published++;
            }
            if (!page.hasNext()) {
                return published;
            }
            page = userRepository.findAll(page.nextPageable());
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void requireNotSelf(Long userId, String message) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (CurrentUser.from(authentication).map(user -> userId.equals(user.userId())).orElse(false)) {
            throw new AccessDeniedException(message);
        }
    }
}

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

import java.util.Set;

@Service
public class AdminUserService {

    // The set of roles an administrator may assign. Free-text roles would be accepted by
    // the column and then silently fail every authority check, producing an account that
    // looks privileged in the directory and is not.
    private static final Set<String> ASSIGNABLE_ROLES = Set.of("USER", "ADMIN");

    private final UserRepository userRepository;
    private final UserEventPublisher userEventPublisher;

    public AdminUserService(UserRepository userRepository, UserEventPublisher userEventPublisher) {
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
        String normalised = role == null ? "" : role.trim().toUpperCase();
        if (!ASSIGNABLE_ROLES.contains(normalised)) {
            throw new IllegalArgumentException("Role must be one of " + ASSIGNABLE_ROLES);
        }

        User user = findUser(userId);
        // Without this an administrator can demote themselves, and if they are the only
        // administrator the role endpoint becomes unreachable for everyone — including
        // the person who would need to undo it.
        requireNotSelf(userId, "You cannot change your own role");

        user.setRole(normalised);
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
        userEventPublisher.publish(
                enabled ? UserEventType.USER_ENABLED : UserEventType.USER_DISABLED, saved);
        return UserSummaryResponse.from(saved);
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
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser
                && userId.equals(currentUser.userId())) {
            throw new AccessDeniedException(message);
        }
    }
}

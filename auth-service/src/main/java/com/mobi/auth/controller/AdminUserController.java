package com.mobi.auth.controller;

import com.mobi.auth.dto.PageResponse;
import com.mobi.auth.dto.UpdateUserRoleRequest;
import com.mobi.auth.dto.UpdateUserStatusRequest;
import com.mobi.auth.dto.UserSummaryResponse;
import com.mobi.auth.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// Everything here is reachable only with an ADMIN authority — enforced in SecurityConfig
// by the /auth/admin/** matcher, not by annotations on these methods, so a new endpoint
// added to this class is protected by default rather than by remembering to mark it.
@RestController
@RequestMapping("/auth/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public PageResponse<UserSummaryResponse> listUsers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return adminUserService.listUsers(search, pageable);
    }

    /**
     * Several users by id in one call, for pages that show records owned by other
     * services (payments, orders) and only know the user id. Unknown ids are skipped.
     */
    @GetMapping("/by-ids")
    public List<UserSummaryResponse> getUsersByIds(@RequestParam List<Long> ids) {
        return adminUserService.getUsersByIds(ids);
    }

    @GetMapping("/{userId}")
    public UserSummaryResponse getUser(@PathVariable Long userId) {
        return adminUserService.getUser(userId);
    }

    @PatchMapping("/{userId}/role")
    public UserSummaryResponse updateRole(@PathVariable Long userId,
                                          @Valid @RequestBody UpdateUserRoleRequest request) {
        return adminUserService.updateRole(userId, request.getRole(), request.getSupplierId());
    }

    @PatchMapping("/{userId}/status")
    public UserSummaryResponse updateStatus(@PathVariable Long userId,
                                            @Valid @RequestBody UpdateUserStatusRequest request) {
        return adminUserService.updateStatus(userId, request.getEnabled());
    }

    @PostMapping("/{userId}/unlock")
    public UserSummaryResponse unlock(@PathVariable Long userId) {
        return adminUserService.unlock(userId);
    }

    // Operational tool, not part of the directory: rebuilds downstream projections by
    // republishing every account's current state.
    @PostMapping("/replay")
    public Map<String, Long> replay() {
        return Map.of("published", adminUserService.replayUserSnapshots());
    }
}

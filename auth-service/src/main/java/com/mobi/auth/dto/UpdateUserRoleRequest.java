package com.mobi.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateUserRoleRequest {

    @NotBlank(message = "Role is required")
    private String role;

    // Required when role is SUPPLIER; ignored otherwise.
    private Long supplierId;

    public UpdateUserRoleRequest() {
    }

    public UpdateUserRoleRequest(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }
}

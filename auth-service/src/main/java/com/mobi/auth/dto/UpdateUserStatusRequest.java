package com.mobi.auth.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateUserStatusRequest {

    // Boxed and @NotNull rather than a primitive: a primitive would silently default a
    // missing field to false and disable the account the caller never asked to touch.
    @NotNull(message = "Enabled is required")
    private Boolean enabled;

    public UpdateUserStatusRequest() {
    }

    public UpdateUserStatusRequest(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}

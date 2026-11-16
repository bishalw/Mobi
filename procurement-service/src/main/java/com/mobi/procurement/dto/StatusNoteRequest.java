package com.mobi.procurement.dto;

import jakarta.validation.constraints.Size;

/** Reason for a reject, cancel or close short. Required for reject and close (checked in the service). */
public record StatusNoteRequest(@Size(max = 500) String note) {
}

package com.clienthub.web.dto.admin;

import com.clienthub.domain.enums.InvoiceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ForceStatusRequest(
        @NotNull(message = "Status is required")
        InvoiceStatus status,

        @NotBlank(message = "Reason is required for force status update")
        String reason
) {
}

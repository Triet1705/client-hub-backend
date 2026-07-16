package com.clienthub.web3.service;

import java.time.Instant;

public record AuditAnchorSummaryResponse(
        long waitingRecords,
        long pendingBatches,
        long confirmedBatches,
        long failedBatches,
        Instant latestConfirmedAt,
        String serviceStatus) {
}

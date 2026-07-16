package com.clienthub.application.service;

import com.clienthub.application.dto.audit.UserAuditProofResponse;
import java.util.Collection;
import java.util.Map;

public interface AuditProofReader {
    UserAuditProofResponse getUserProof(long auditLogId);

    UserAuditProofResponse verifyUserProof(long auditLogId);

    Map<Long, UserAuditProofResponse> getUserProofs(Collection<Long> auditLogIds);
}

package com.clienthub.web3.service;

import com.clienthub.domain.entity.AuditLog;
import com.clienthub.domain.enums.AuditAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditMerkleServiceTest {
    private final AuditMerkleService service = new AuditMerkleService(new ObjectMapper());

    @Test
    void buildsDeterministicProofsForOddAndEvenTrees() {
        for (int count : List.of(1, 2, 3, 4, 5)) {
            List<AuditLog> logs = java.util.stream.IntStream.rangeClosed(1, count)
                    .mapToObj(index -> auditLog((long) index, "{\"value\":" + index + "}"))
                    .toList();

            AuditMerkleService.MerkleBatch batch = service.build(logs);

            assertThat(batch.leaves()).hasSize(count);
            batch.leaves().forEach(leaf ->
                    assertThat(service.verify(leaf.hash(), leaf.proof(), batch.root())).isTrue());
            assertThat(service.build(logs).root()).isEqualTo(batch.root());
        }
    }

    @Test
    void detectsChangedAuditContentAndInvalidProofs() {
        AuditLog original = auditLog(1L, "{\"a\":1,\"b\":2}");
        AuditLog sameCanonicalJson = auditLog(1L, "{\"b\":2,\"a\":1}");
        AuditLog changed = auditLog(1L, "{\"a\":9,\"b\":2}");

        assertThat(service.leafHash(original)).isEqualTo(service.leafHash(sameCanonicalJson));
        assertThat(service.leafHash(changed)).isNotEqualTo(service.leafHash(original));

        AuditMerkleService.MerkleBatch batch = service.build(List.of(original, auditLog(2L, "{}")));
        assertThat(service.verify(service.leafHash(changed), batch.leaves().getFirst().proof(), batch.root())).isFalse();
    }

    private AuditLog auditLog(Long id, String newValue) {
        AuditLog log = new AuditLog("default", null, "admin@clienthub.io", "ADMIN",
                AuditAction.UPDATE, "PROJECT", "project-1", null, newValue,
                "127.0.0.1", "legacy-data-hash");
        ReflectionTestUtils.setField(log, "id", id);
        ReflectionTestUtils.setField(log, "createdAt", Instant.parse("2026-07-12T00:00:00Z"));
        return log;
    }
}

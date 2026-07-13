package com.clienthub.web3.service;

import com.clienthub.application.service.AuditService;
import com.clienthub.application.exception.ResourceNotFoundException;
import com.clienthub.domain.entity.*;
import com.clienthub.domain.enums.*;
import com.clienthub.domain.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.time.*;
import java.util.*;

@Service
public class AuditAnchorService {
    private static final Logger log = LoggerFactory.getLogger(AuditAnchorService.class);
    private static final List<AuditAction> EXCLUDED_ACTIONS =
            List.of(AuditAction.ANCHOR_SUCCESS, AuditAction.ANCHOR_FAILED);

    private final AuditLogRepository auditLogRepository;
    private final AuditAnchorBatchRepository batchRepository;
    private final AuditAnchorMemberRepository memberRepository;
    private final AuditMerkleService merkleService;
    private final AuditAnchorBlockchainService blockchainService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${audit.anchor.enabled:false}") private boolean enabled;
    @Value("${audit.anchor.batch_size:1000}") private int batchSize;
    @Value("${audit.anchor.max_age_hours:24}") private int maxAgeHours;
    @Value("${audit.anchor.max_attempts:5}") private int maxAttempts;
    @Value("${blockchain.required_confirmations:12}") private int requiredConfirmations;

    public AuditAnchorService(AuditLogRepository auditLogRepository,
                              AuditAnchorBatchRepository batchRepository,
                              AuditAnchorMemberRepository memberRepository,
                              AuditMerkleService merkleService,
                              AuditAnchorBlockchainService blockchainService,
                              AuditService auditService,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.auditLogRepository = auditLogRepository;
        this.batchRepository = batchRepository;
        this.memberRepository = memberRepository;
        this.merkleService = merkleService;
        this.blockchainService = blockchainService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Optional<AuditAnchorBatchResponse> run(boolean force) {
        if (!enabled) return Optional.empty();
        UUID batchId = transactionTemplate.execute(status -> createBatch(force));
        if (batchId == null) return Optional.empty();
        submit(batchId);
        return batchRepository.findById(batchId).map(AuditAnchorBatchResponse::from);
    }

    public void submitPending() {
        if (!enabled || !blockchainService.isReady()) return;
        List<AuditAnchorBatch> pending = batchRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(AuditAnchorBatchStatus.READY, AuditAnchorBatchStatus.FAILED));
        for (AuditAnchorBatch batch : pending) {
            boolean retryDue = batch.getNextAttemptAt() == null || !batch.getNextAttemptAt().isAfter(Instant.now());
            if (batch.getAttempts() < Math.max(1, maxAttempts) && retryDue) submit(batch.getId());
        }
    }

    public void reconcileSubmitted() {
        if (!enabled || !blockchainService.isReady()) return;
        List<AuditAnchorBatch> batches = batchRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(AuditAnchorBatchStatus.SUBMITTED));
        for (AuditAnchorBatch batch : batches) reconcile(batch.getId());
    }

    public Page<AuditAnchorBatchResponse> listBatches(Pageable pageable) {
        return batchRepository.findAllByOrderByCreatedAtDesc(pageable).map(AuditAnchorBatchResponse::from);
    }

    public AuditProofResponse getProof(long auditLogId) {
        AuditLog logEntry = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog", "id", auditLogId));
        return memberRepository.findByAuditLogId(auditLogId)
                .map(member -> response(logEntry, member, localStatus(logEntry, member)))
                .orElseGet(() -> notAnchored(auditLogId));
    }

    public AuditProofResponse verify(long auditLogId) {
        AuditLog logEntry = auditLogRepository.findById(auditLogId)
                .orElseThrow(() -> new ResourceNotFoundException("AuditLog", "id", auditLogId));
        Optional<AuditAnchorMember> optionalMember = memberRepository.findByAuditLogId(auditLogId);
        if (optionalMember.isEmpty()) return notAnchored(auditLogId);

        AuditAnchorMember member = optionalMember.get();
        AuditVerificationStatus local = localStatus(logEntry, member);
        if (local == AuditVerificationStatus.TAMPERED) return response(logEntry, member, local);
        if (member.getBatch().getStatus() != AuditAnchorBatchStatus.CONFIRMED) {
            return response(logEntry, member, AuditVerificationStatus.PENDING);
        }

        try {
            Optional<AuditAnchorBlockchainService.OnChainAnchor> anchor =
                    blockchainService.readAnchor(member.getBatch().getMerkleRoot());
            if (anchor.isEmpty()) return response(logEntry, member, AuditVerificationStatus.TAMPERED);
            AuditAnchorBlockchainService.OnChainAnchor value = anchor.get();
            boolean matches = value.recordCount() == member.getBatch().getRecordCount()
                    && value.metadataHash().equalsIgnoreCase(member.getBatch().getMetadataHash());
            return response(logEntry, member,
                    matches ? AuditVerificationStatus.VERIFIED : AuditVerificationStatus.TAMPERED);
        } catch (AuditAnchorBlockchainService.BlockchainUnavailableException e) {
            return response(logEntry, member, AuditVerificationStatus.CHAIN_UNAVAILABLE);
        }
    }

    private UUID createBatch(boolean force) {
        long waiting = auditLogRepository.countUnassignedForAnchoring(EXCLUDED_ACTIONS);
        Optional<Instant> oldest = auditLogRepository.findOldestUnassignedCreatedAt(EXCLUDED_ACTIONS);
        boolean dueByAge = oldest.map(value -> value.isBefore(Instant.now().minus(Duration.ofHours(maxAgeHours)))).orElse(false);
        if (waiting == 0 || (!force && waiting < batchSize && !dueByAge)) return null;

        int limit = Math.max(1, batchSize);
        List<AuditLog> logs = auditLogRepository.findUnassignedForAnchoring(limit);
        if (logs.isEmpty()) return null;

        AuditMerkleService.MerkleBatch tree = merkleService.build(logs);
        AuditAnchorBatch batch = new AuditAnchorBatch();
        batch.setId(UUID.randomUUID());
        batch.setStatus(AuditAnchorBatchStatus.READY);
        batch.setMerkleRoot(tree.root());
        batch.setMetadataHash(merkleService.metadataHash(logs.getFirst().getId(), logs.getLast().getId(), logs.size()));
        batch.setHashVersion(AuditMerkleService.HASH_VERSION);
        batch.setFirstLogId(logs.getFirst().getId());
        batch.setLastLogId(logs.getLast().getId());
        batch.setRecordCount(logs.size());
        batchRepository.saveAndFlush(batch);

        for (AuditMerkleService.MerkleLeaf leaf : tree.leaves()) {
            memberRepository.save(new AuditAnchorMember(batch, leaf.auditLogId(), leaf.index(), leaf.hash(), writeProof(leaf.proof())));
        }
        memberRepository.flush();
        return batch.getId();
    }

    private void submit(UUID batchId) {
        transactionTemplate.executeWithoutResult(status -> {
            AuditAnchorBatch batch = batchRepository.findById(batchId).orElseThrow();
            if (batch.getStatus() == AuditAnchorBatchStatus.SUBMITTED || batch.getStatus() == AuditAnchorBatchStatus.CONFIRMED) return;
            batch.setAttempts(batch.getAttempts() + 1);
            batch.setLastError(null);
            batch.setNextAttemptAt(null);
            try {
                AuditAnchorBlockchainService.Submission submission = blockchainService.submit(
                        batch.getMerkleRoot(), batch.getRecordCount(), batch.getMetadataHash());
                batch.setTransactionHash(submission.transactionHash());
                batch.setChainId(submission.chainId());
                batch.setContractAddress(submission.contractAddress());
                batch.setSubmittedAt(Instant.now());
                batch.setStatus(AuditAnchorBatchStatus.SUBMITTED);
            } catch (Exception e) {
                batch.setStatus(AuditAnchorBatchStatus.FAILED);
                batch.setLastError(limit(e.getMessage()));
                batch.setNextAttemptAt(Instant.now().plus(retryDelay(batch.getAttempts())));
                log.error("Audit anchor submission failed for batch {}", batchId, e);
                auditService.log(AuditAction.ANCHOR_FAILED, "AUDIT_ANCHOR_BATCH", batchId.toString(), null,
                        Map.of("root", batch.getMerkleRoot(), "error", limit(e.getMessage())), null);
            }
        });
    }

    private void reconcile(UUID batchId) {
        transactionTemplate.executeWithoutResult(status -> {
            AuditAnchorBatch batch = batchRepository.findById(batchId).orElseThrow();
            try {
                Optional<TransactionReceipt> optionalReceipt = blockchainService.receipt(batch.getTransactionHash());
                if (optionalReceipt.isEmpty()) return;
                TransactionReceipt receipt = optionalReceipt.get();
                if (!receipt.isStatusOK()) {
                    batch.setStatus(AuditAnchorBatchStatus.FAILED);
                    batch.setLastError("Anchor transaction reverted");
                    batch.setNextAttemptAt(Instant.now().plus(retryDelay(batch.getAttempts())));
                    return;
                }
                int confirmations = blockchainService.confirmations(receipt.getBlockNumber());
                batch.setSubmittedBlock(receipt.getBlockNumber());
                batch.setConfirmations(confirmations);
                if (confirmations < Math.max(1, requiredConfirmations)) return;

                Optional<AuditAnchorBlockchainService.OnChainAnchor> anchor =
                        blockchainService.readAnchor(batch.getMerkleRoot());
                if (anchor.isEmpty()
                        || anchor.get().recordCount() != batch.getRecordCount()
                        || !anchor.get().metadataHash().equalsIgnoreCase(batch.getMetadataHash())) {
                    batch.setStatus(AuditAnchorBatchStatus.FAILED);
                    batch.setLastError("On-chain anchor metadata mismatch");
                    batch.setNextAttemptAt(Instant.now().plus(retryDelay(batch.getAttempts())));
                    return;
                }
                batch.setStatus(AuditAnchorBatchStatus.CONFIRMED);
                batch.setConfirmedBlock(receipt.getBlockNumber());
                batch.setConfirmedAt(Instant.now());
                auditService.log(AuditAction.ANCHOR_SUCCESS, "AUDIT_ANCHOR_BATCH", batchId.toString(), null,
                        Map.of("root", batch.getMerkleRoot(), "transactionHash", batch.getTransactionHash()), null);
            } catch (AuditAnchorBlockchainService.BlockchainUnavailableException e) {
                log.warn("Audit anchor reconciliation deferred for batch {}: {}", batchId, e.getMessage());
            }
        });
    }

    private AuditVerificationStatus localStatus(AuditLog logEntry, AuditAnchorMember member) {
        List<String> proof = readProof(member.getMerkleProof());
        boolean leafMatches = merkleService.leafHash(logEntry).equalsIgnoreCase(member.getLeafHash());
        boolean rootMatches = merkleService.verify(member.getLeafHash(), proof, member.getBatch().getMerkleRoot());
        if (!leafMatches || !rootMatches) return AuditVerificationStatus.TAMPERED;
        return member.getBatch().getStatus() == AuditAnchorBatchStatus.CONFIRMED
                ? AuditVerificationStatus.VERIFIED : AuditVerificationStatus.PENDING;
    }

    private AuditProofResponse response(AuditLog logEntry, AuditAnchorMember member, AuditVerificationStatus status) {
        AuditAnchorBatch batch = member.getBatch();
        return new AuditProofResponse(logEntry.getId(), batch.getId(), batch.getStatus().name(), status,
                batch.getHashVersion(), member.getLeafHash(), member.getLeafIndex(), readProof(member.getMerkleProof()),
                batch.getMerkleRoot(), batch.getMetadataHash(), batch.getChainId(), batch.getContractAddress(),
                batch.getTransactionHash(), batch.getSubmittedBlock(), batch.getConfirmedBlock(), batch.getConfirmations());
    }

    private AuditProofResponse notAnchored(long auditLogId) {
        return new AuditProofResponse(auditLogId, null, null, AuditVerificationStatus.NOT_ANCHORED,
                AuditMerkleService.HASH_VERSION, null, 0, List.of(), null, null,
                null, null, null, null, null, 0);
    }

    private String writeProof(List<String> proof) {
        try { return objectMapper.writeValueAsString(proof); }
        catch (Exception e) { throw new IllegalStateException("Unable to store Merkle proof", e); }
    }

    private List<String> readProof(String proof) {
        try { return objectMapper.readValue(proof, new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Unable to read Merkle proof", e); }
    }

    private String limit(String value) {
        if (value == null || value.isBlank()) return "Unknown anchoring error";
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private Duration retryDelay(int attempt) {
        long multiplier = 1L << Math.min(Math.max(0, attempt - 1), 4);
        return Duration.ofMinutes(Math.min(60, 5 * multiplier));
    }
}

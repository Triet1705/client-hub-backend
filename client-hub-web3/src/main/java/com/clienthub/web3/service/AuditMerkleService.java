package com.clienthub.web3.service;

import com.clienthub.domain.entity.AuditLog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class AuditMerkleService {
    public static final String HASH_VERSION = "CLIENT_HUB_AUDIT_LEAF_V1";
    private final ObjectMapper objectMapper;

    public AuditMerkleService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MerkleBatch build(List<AuditLog> orderedLogs) {
        if (orderedLogs.isEmpty()) throw new IllegalArgumentException("Cannot build an empty Merkle tree");

        List<byte[]> leaves = orderedLogs.stream().map(this::leafBytes).toList();
        List<List<String>> proofs = new ArrayList<>();
        for (int i = 0; i < leaves.size(); i++) proofs.add(new ArrayList<>());

        List<Node> level = new ArrayList<>();
        for (int i = 0; i < leaves.size(); i++) level.add(new Node(leaves.get(i), List.of(i)));

        while (level.size() > 1) {
            List<Node> next = new ArrayList<>();
            for (int i = 0; i < level.size(); i += 2) {
                Node left = level.get(i);
                Node right = i + 1 < level.size() ? level.get(i + 1) : left;
                for (int index : left.leafIndexes()) proofs.get(index).add(hex(right.hash()));
                if (right != left) {
                    for (int index : right.leafIndexes()) proofs.get(index).add(hex(left.hash()));
                }
                List<Integer> indexes = new ArrayList<>(left.leafIndexes());
                if (right != left) indexes.addAll(right.leafIndexes());
                next.add(new Node(hashPair(left.hash(), right.hash()), indexes));
            }
            level = next;
        }

        List<MerkleLeaf> resultLeaves = new ArrayList<>();
        for (int i = 0; i < orderedLogs.size(); i++) {
            resultLeaves.add(new MerkleLeaf(orderedLogs.get(i).getId(), i, hex(leaves.get(i)), List.copyOf(proofs.get(i))));
        }
        return new MerkleBatch(hex(level.getFirst().hash()), List.copyOf(resultLeaves));
    }

    public String leafHash(AuditLog log) {
        return hex(leafBytes(log));
    }

    public boolean verify(String leafHash, List<String> proof, String expectedRoot) {
        byte[] current = bytes(leafHash);
        for (String sibling : proof) current = hashPair(current, bytes(sibling));
        return hex(current).equalsIgnoreCase(expectedRoot);
    }

    public String metadataHash(long firstId, long lastId, int count) {
        return hex(sha256((HASH_VERSION + "|" + firstId + "|" + lastId + "|" + count)
                .getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] leafBytes(AuditLog log) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("schemaVersion", HASH_VERSION);
            root.put("id", log.getId());
            put(root, "tenantId", log.getTenantId());
            put(root, "userId", log.getUserId() == null ? null : log.getUserId().toString());
            put(root, "userEmail", log.getUserEmail());
            put(root, "userRole", log.getUserRole());
            put(root, "action", log.getAction() == null ? null : log.getAction().name());
            put(root, "entityType", log.getEntityType());
            put(root, "entityId", log.getEntityId());
            root.set("oldValue", parsePayload(log.getOldValue()));
            root.set("newValue", parsePayload(log.getNewValue()));
            put(root, "ipAddress", log.getIpAddress());
            put(root, "createdAt", log.getCreatedAt() == null ? null : log.getCreatedAt().toString());
            put(root, "dataHash", log.getDataHash());
            JsonNode canonical = canonicalize(root);
            return sha256(objectMapper.writeValueAsBytes(canonical));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to canonicalize audit log " + log.getId(), e);
        }
    }

    private JsonNode parsePayload(String value) {
        if (value == null) return objectMapper.nullNode();
        try { return objectMapper.readTree(value); }
        catch (Exception ignored) { return objectMapper.getNodeFactory().textNode(value); }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            sorted.forEach((key, value) -> result.set(key, canonicalize(value)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return node;
    }

    private void put(ObjectNode node, String key, String value) {
        if (value == null) node.putNull(key); else node.put(key, value);
    }

    private byte[] hashPair(byte[] first, byte[] second) {
        byte[] left = Arrays.compareUnsigned(first, second) <= 0 ? first : second;
        byte[] right = left == first ? second : first;
        byte[] joined = new byte[left.length + right.length];
        System.arraycopy(left, 0, joined, 0, left.length);
        System.arraycopy(right, 0, joined, left.length, right.length);
        return sha256(joined);
    }

    private byte[] sha256(byte[] input) {
        try { return MessageDigest.getInstance("SHA-256").digest(input); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private String hex(byte[] value) { return "0x" + HexFormat.of().formatHex(value); }
    private byte[] bytes(String value) { return HexFormat.of().parseHex(value.startsWith("0x") ? value.substring(2) : value); }

    private record Node(byte[] hash, List<Integer> leafIndexes) {}
    public record MerkleLeaf(Long auditLogId, int index, String hash, List<String> proof) {}
    public record MerkleBatch(String root, List<MerkleLeaf> leaves) {}
}

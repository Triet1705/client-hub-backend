package com.clienthub.web3.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

@Service
public class AuditAnchorBlockchainService {
    private static final Logger log = LoggerFactory.getLogger(AuditAnchorBlockchainService.class);

    @Value("${audit.anchor.enabled:false}") private boolean enabled;
    @Value("${blockchain.node_url:http://localhost:8545}") private String nodeUrl;
    @Value("${blockchain.admin_private_key:}") private String privateKey;
    @Value("${audit.anchor.contract_address:}") private String contractAddress;

    private Web3j web3j;
    private RawTransactionManager transactionManager;
    private long chainId;

    @PostConstruct
    void init() {
        if (!enabled) return;
        if (nodeUrl.isBlank() || privateKey.isBlank() || contractAddress.isBlank()) {
            log.warn("Audit anchoring is enabled but node URL, wallet key, or contract address is missing");
            return;
        }
        try {
            web3j = Web3j.build(new HttpService(nodeUrl));
            chainId = web3j.ethChainId().send().getChainId().longValueExact();
            transactionManager = new RawTransactionManager(web3j, Credentials.create(privateKey), chainId);
        } catch (Exception e) {
            log.error("Unable to initialize audit anchor blockchain client", e);
            web3j = null;
            transactionManager = null;
        }
    }

    public boolean isReady() {
        return enabled && web3j != null && transactionManager != null && !contractAddress.isBlank();
    }

    public synchronized Submission submit(String root, int recordCount, String metadataHash) throws Exception {
        requireReady();
        Function function = new Function("anchorRoot",
                List.of(bytes32(root), new Uint256(recordCount), bytes32(metadataHash)), List.of());
        var response = transactionManager.sendTransaction(
                DefaultGasProvider.GAS_PRICE,
                BigInteger.valueOf(350_000),
                contractAddress,
                FunctionEncoder.encode(function),
                BigInteger.ZERO);
        if (response.hasError()) throw new IllegalStateException(response.getError().getMessage());
        return new Submission(response.getTransactionHash(), chainId, contractAddress);
    }

    public Optional<TransactionReceipt> receipt(String transactionHash) {
        if (!isReady() || transactionHash == null || transactionHash.isBlank()) return Optional.empty();
        try { return web3j.ethGetTransactionReceipt(transactionHash).send().getTransactionReceipt(); }
        catch (Exception e) { throw new BlockchainUnavailableException("Unable to read anchoring receipt", e); }
    }

    public int confirmations(BigInteger blockNumber) {
        if (!isReady() || blockNumber == null) return 0;
        try {
            BigInteger current = web3j.ethBlockNumber().send().getBlockNumber();
            if (current.compareTo(blockNumber) < 0) return 0;
            return current.subtract(blockNumber).add(BigInteger.ONE)
                    .min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        } catch (Exception e) {
            throw new BlockchainUnavailableException("Unable to read current block", e);
        }
    }

    public Optional<OnChainAnchor> readAnchor(String root) {
        if (!isReady()) throw new BlockchainUnavailableException("Audit anchor blockchain client is not ready");
        try {
            Function function = new Function("anchors", List.of(bytes32(root)), List.of(
                    new TypeReference<Uint256>() {}, new TypeReference<Bytes32>() {},
                    new TypeReference<Uint256>() {}, new TypeReference<Address>() {}));
            String value = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, contractAddress, FunctionEncoder.encode(function)),
                    DefaultBlockParameterName.LATEST).send().getValue();
            List<Type> decoded = FunctionReturnDecoder.decode(value, function.getOutputParameters());
            if (decoded.size() != 4 || ((Uint256) decoded.get(2)).getValue().signum() == 0) return Optional.empty();
            return Optional.of(new OnChainAnchor(
                    ((Uint256) decoded.get(0)).getValue().intValueExact(),
                    Numeric.toHexString(((Bytes32) decoded.get(1)).getValue()),
                    ((Uint256) decoded.get(2)).getValue(),
                    ((Address) decoded.get(3)).getValue()));
        } catch (BlockchainUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new BlockchainUnavailableException("Unable to read anchored root", e);
        }
    }

    private Bytes32 bytes32(String hex) {
        byte[] value = Numeric.hexStringToByteArray(hex);
        if (value.length != 32) throw new IllegalArgumentException("Expected bytes32 value");
        return new Bytes32(value);
    }

    private void requireReady() {
        if (!isReady()) throw new BlockchainUnavailableException("Audit anchor blockchain client is not ready");
    }

    public record Submission(String transactionHash, long chainId, String contractAddress) {}
    public record OnChainAnchor(int recordCount, String metadataHash, BigInteger anchoredAt, String submitter) {}

    public static class BlockchainUnavailableException extends RuntimeException {
        public BlockchainUnavailableException(String message) { super(message); }
        public BlockchainUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}

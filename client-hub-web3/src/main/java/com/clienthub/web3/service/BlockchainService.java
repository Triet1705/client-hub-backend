package com.clienthub.web3.service;

import com.clienthub.domain.enums.EscrowStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class BlockchainService {
    private static final Logger log = LoggerFactory.getLogger(BlockchainService.class);

    @Value("${blockchain.enabled:false}")
    private boolean blockchainEnabled;

    @Value("${blockchain.node_url:http://localhost:8545}")
    private String nodeUrl;

    @Value("${blockchain.contract_address:}")
    private String contractAddress;

    private Web3j web3j;

    @PostConstruct
    public void init() {
        if (blockchainEnabled) {
            this.web3j = Web3j.build(new HttpService(nodeUrl));
        }
    }

    public EscrowStatus getEscrowStatus(Long invoiceId) {
        return getEscrowSnapshot(invoiceId)
                .map(EscrowSnapshot::status)
                .orElse(EscrowStatus.NOT_STARTED);
    }

    public Optional<EscrowSnapshot> getEscrowSnapshot(Long invoiceId) {
        if (!blockchainEnabled || web3j == null || contractAddress == null || contractAddress.isEmpty()) {
            return Optional.empty();
        }

        try {
            Function function = new Function("escrows",
                    Arrays.asList(new Uint256(BigInteger.valueOf(invoiceId))),
                    Arrays.asList(
                            new TypeReference<Address>() {},
                            new TypeReference<Address>() {},
                            new TypeReference<Address>() {},
                            new TypeReference<Uint256>() {},
                            new TypeReference<Uint8>() {}));

            String encodedFunction = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, contractAddress, encodedFunction),
                    DefaultBlockParameterName.LATEST).send();

            String value = response.getValue();
            if (value == null || value.equals("0x")) {
                return Optional.empty();
            }

            List<Type> decoded = FunctionReturnDecoder.decode(value, function.getOutputParameters());
            if (decoded.size() < 5) {
                return Optional.empty();
            }

            String clientAddress = ((Address) decoded.get(0)).getValue();
            String freelancerAddress = ((Address) decoded.get(1)).getValue();
            String tokenAddress = ((Address) decoded.get(2)).getValue();
            BigInteger amount = ((Uint256) decoded.get(3)).getValue();
            int statusCode = ((Uint8) decoded.get(4)).getValue().intValue();

            return Optional.of(new EscrowSnapshot(
                    clientAddress,
                    freelancerAddress,
                    tokenAddress,
                    amount,
                    mapEscrowStatus(statusCode)));
            
        } catch (Exception e) {
            log.error("Error getting escrow snapshot", e);
            return Optional.empty();
        }
    }

    public int getConfirmationsSince(BigInteger blockNumber) {
        if (blockNumber == null || blockNumber.signum() < 0 || !blockchainEnabled || web3j == null) {
            return 0;
        }

        try {
            BigInteger currentBlock = web3j.ethBlockNumber().send().getBlockNumber();
            if (currentBlock.compareTo(blockNumber) < 0) {
                return 0;
            }
            BigInteger confirmations = currentBlock.subtract(blockNumber).add(BigInteger.ONE);
            return confirmations.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValue();
        } catch (Exception e) {
            log.error("Error calculating blockchain confirmations", e);
            return 0;
        }
    }

    public Optional<BigInteger> getTransactionBlockNumber(String transactionHash) {
        if (transactionHash == null || transactionHash.isBlank() || !blockchainEnabled || web3j == null) {
            return Optional.empty();
        }

        try {
            EthGetTransactionReceipt response = web3j.ethGetTransactionReceipt(transactionHash).send();
            return response.getTransactionReceipt().map(TransactionReceipt::getBlockNumber);
        } catch (Exception e) {
            log.error("Error getting transaction receipt for {}", transactionHash, e);
            return Optional.empty();
        }
    }

    public Optional<BlockchainEventReference> findEventReference(String eventTopic, Long invoiceId) {
        if (eventTopic == null || eventTopic.isBlank() || invoiceId == null
                || !blockchainEnabled || web3j == null || contractAddress == null || contractAddress.isEmpty()) {
            return Optional.empty();
        }

        try {
            EthFilter filter = new EthFilter(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST, contractAddress);
            filter.addSingleTopic(eventTopic);
            filter.addSingleTopic(Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(invoiceId), 64));

            EthLog response = web3j.ethGetLogs(filter).send();
            List<EthLog.LogResult> logs = response.getLogs();
            for (int index = logs.size() - 1; index >= 0; index--) {
                Object rawLog = logs.get(index).get();
                if (rawLog instanceof org.web3j.protocol.core.methods.response.Log eventLog) {
                    return Optional.of(new BlockchainEventReference(
                            eventLog.getTransactionHash(),
                            eventLog.getBlockNumber()));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error finding blockchain event reference for invoice {}", invoiceId, e);
            return Optional.empty();
        }
    }

    private EscrowStatus mapEscrowStatus(int statusCode) {
        return switch (statusCode) {
            case 0 -> EscrowStatus.NOT_STARTED;
            case 1 -> EscrowStatus.DEPOSITED;
            case 2 -> EscrowStatus.RELEASED;
            case 3 -> EscrowStatus.REFUNDED;
            default -> {
                log.warn("Unknown Solidity EscrowStatus code: {}", statusCode);
                yield EscrowStatus.NOT_STARTED;
            }
        };
    }

    public Web3j getWeb3j() {
        return web3j;
    }
}

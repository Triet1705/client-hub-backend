package com.clienthub.web3.service;

import com.clienthub.domain.enums.EscrowStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
        if (!blockchainEnabled || web3j == null || contractAddress == null || contractAddress.isEmpty()) {
            return EscrowStatus.NOT_STARTED;
        }

        try {
            Function function = new Function("getEscrowStatus",
                    Arrays.asList(new Uint256(BigInteger.valueOf(invoiceId))),
                    Arrays.asList(new TypeReference<Uint8>() {}));

            String encodedFunction = FunctionEncoder.encode(function);
            EthCall response = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, contractAddress, encodedFunction),
                    DefaultBlockParameterName.LATEST).send();

            String value = response.getValue();
            if (value == null || value.equals("0x")) {
                return EscrowStatus.NOT_STARTED;
            }

            List<Type> decoded = FunctionReturnDecoder.decode(value, function.getOutputParameters());
            if (decoded.isEmpty()) {
                return EscrowStatus.NOT_STARTED;
            }

            int statusCode = ((Uint8) decoded.get(0)).getValue().intValue();
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
            
        } catch (Exception e) {
            log.error("Error getting escrow status", e);
            return EscrowStatus.NOT_STARTED;
        }
    }

    public Web3j getWeb3j() {
        return web3j;
    }
}

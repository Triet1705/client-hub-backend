package com.clienthub.web3.listener;

import com.clienthub.common.context.TenantContext;
import com.clienthub.domain.entity.Invoice;
import com.clienthub.domain.enums.EscrowStatus;
import com.clienthub.domain.enums.InvoiceStatus;
import com.clienthub.domain.repository.InvoiceRepository;
import com.clienthub.web3.service.BlockchainService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.utils.Numeric;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class BlockchainListener {
    private static final Logger log = LoggerFactory.getLogger(BlockchainListener.class);

    private final BlockchainService blockchainService;

    @Value("${blockchain.enabled:false}")
    private boolean blockchainEnabled;

    @Value("${blockchain.contract_address:}")
    private String contractAddress;

    // Events from FreelanceEscrow.sol
    public static final Event DEPOSITED_EVENT = new Event("Deposited",
            Arrays.asList(new TypeReference<Uint256>(true) {}, new TypeReference<Address>() {}, new TypeReference<Uint256>() {}, new TypeReference<Address>() {}, new TypeReference<Address>() {}));

    public static final Event RELEASED_EVENT = new Event("Released",
            Arrays.asList(new TypeReference<Uint256>(true) {}, new TypeReference<Address>() {}, new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));

    public static final Event REFUNDED_EVENT = new Event("Refunded",
            Arrays.asList(new TypeReference<Uint256>(true) {}, new TypeReference<Address>() {}, new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));

    private final com.clienthub.domain.repository.InvoiceRepository invoiceRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    public BlockchainListener(com.clienthub.web3.service.BlockchainService blockchainService, 
                              com.clienthub.domain.repository.InvoiceRepository invoiceRepository,
                              org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.blockchainService = blockchainService;
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void subscribeToEvents() {
        if (!blockchainEnabled || contractAddress == null || contractAddress.isEmpty()) {
            return;
        }

        String depositedTopic = EventEncoder.encode(DEPOSITED_EVENT);
        String releasedTopic = EventEncoder.encode(RELEASED_EVENT);
        String refundedTopic = EventEncoder.encode(REFUNDED_EVENT);

        EthFilter filter = new EthFilter(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST, contractAddress);
        filter.addOptionalTopics(depositedTopic, releasedTopic, refundedTopic);

        blockchainService.getWeb3j().ethLogFlowable(filter).subscribe(logEvent -> {
            TenantContext.setSystemContext();
            try {
                String topic = logEvent.getTopics().get(0);
                
                // Extract invoiceId from indexed parameter (topic 1)
                if (logEvent.getTopics().size() > 1) {
                    Long invoiceId = Numeric.decodeQuantity(logEvent.getTopics().get(1)).longValue();
                    String txHash = logEvent.getTransactionHash();

                Optional<Invoice> optInvoice = invoiceRepository.findSystemCryptoEscrowById(invoiceId);
                if (optInvoice.isPresent()) {
                    Invoice invoice = optInvoice.get();

                    invoice.setTxHash(txHash);
                    
                    if (topic.equals(depositedTopic)) {
                        invoice.setEscrowStatus(EscrowStatus.DEPOSITED);
                        invoice.setStatus(InvoiceStatus.LOCKED);
                    } else if (topic.equals(releasedTopic)) {
                        invoice.setEscrowStatus(EscrowStatus.RELEASED);
                        invoice.setStatus(InvoiceStatus.PAID);
                        invoice.setPaidAt(Instant.now());
                    } else if (topic.equals(refundedTopic)) {
                        invoice.setEscrowStatus(EscrowStatus.REFUNDED);
                        invoice.setStatus(InvoiceStatus.REFUNDED);
                    }
                    
                    invoiceRepository.save(invoice);
                    
                    eventPublisher.publishEvent(new com.clienthub.domain.event.InvoiceStatusChangedEvent(this, invoice));
                }
            }
            } finally {
                TenantContext.clear();
            }
        }, error -> {
            log.error("Error subscribing to blockchain events", error);
        });
    }
}

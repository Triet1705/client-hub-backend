package com.clienthub.web3.service;

import java.util.Arrays;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;

public final class EscrowContractEvents {
    private EscrowContractEvents() {}

    public static final Event DEPOSITED_EVENT = new Event("Deposited",
            Arrays.asList(new TypeReference<Uint256>(true) {}, new TypeReference<Address>() {},
                    new TypeReference<Uint256>() {}, new TypeReference<Address>() {},
                    new TypeReference<Address>() {}));

    public static final Event RELEASED_EVENT = new Event("Released",
            Arrays.asList(new TypeReference<Uint256>(true) {}, new TypeReference<Address>() {},
                    new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));

    public static final Event REFUNDED_EVENT = new Event("Refunded",
            Arrays.asList(new TypeReference<Uint256>(true) {}, new TypeReference<Address>() {},
                    new TypeReference<Address>() {}, new TypeReference<Uint256>() {}));

    public static final String DEPOSITED_TOPIC = EventEncoder.encode(DEPOSITED_EVENT);
    public static final String RELEASED_TOPIC = EventEncoder.encode(RELEASED_EVENT);
    public static final String REFUNDED_TOPIC = EventEncoder.encode(REFUNDED_EVENT);
}

package com.clienthub.web3.service;

import com.clienthub.domain.enums.EscrowStatus;
import java.math.BigInteger;

public record EscrowSnapshot(
        String clientAddress,
        String freelancerAddress,
        String tokenAddress,
        BigInteger amount,
        EscrowStatus status) {}

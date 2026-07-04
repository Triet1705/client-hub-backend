package com.clienthub.web3.service;

import java.math.BigInteger;

public record BlockchainEventReference(String transactionHash, BigInteger blockNumber) {}

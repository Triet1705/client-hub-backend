package com.clienthub.application.exception;

/**
 * Raised when an Ethereum address is already owned by another Client Hub account.
 */
public class WalletAlreadyBoundException extends RuntimeException {

    public WalletAlreadyBoundException() {
        super("This wallet is already bound to another Client Hub account.");
    }
}

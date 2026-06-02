package com.cryptomaster.exception;

public class CoinGeckoException extends RuntimeException {
    public CoinGeckoException(String message) {
        super(message);
    }
    public CoinGeckoException(String message, Throwable cause) {
        super(message, cause);
    }
}
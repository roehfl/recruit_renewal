package com.shinyoung.recruit.common.crypto;

import org.springframework.stereotype.Component;

@Component
public class CryptoHolder {
    private static AesCryptoUtil aes;

    public CryptoHolder(AesCryptoUtil aesCryptoUtil) {
        CryptoHolder.aes = aesCryptoUtil;
    }

    public static AesCryptoUtil get() {
        return aes;
    }
}

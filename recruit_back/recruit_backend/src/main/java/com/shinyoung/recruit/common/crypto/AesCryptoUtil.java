package com.shinyoung.recruit.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class AesCryptoUtil {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int KEY_SIZE = 32;

    private final byte[] key;

    public AesCryptoUtil(String secretKey) {
        this.key = secretKey.getBytes();
        // 키가 틀리면 첫 암호화 때가 아니라 기동할 때 실패시킨다(AES-256 = 32바이트).
        if (key.length != KEY_SIZE) {
            throw new IllegalStateException("crypto.aes.key(AES_SECRET_KEY)는 " + KEY_SIZE + "바이트여야 합니다: 현재 " + key.length);
        }
    }

    public String encrypt(String plainText) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);

            byte[] iv = new byte[16];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes());

            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);

        } catch (Exception e) {
            throw new RuntimeException("AES encrypt error", e);
        }
    }

    public String decrypt(String cipherText) {

        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);

            byte[] iv = new byte[16];
            byte[] encrypted = new byte[combined.length - 16];

            System.arraycopy(combined, 0, iv, 0, 16);
            System.arraycopy(combined, 16, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted);

        } catch (Exception e) {
            throw new RuntimeException("AES decrypt error", e);
        }
    }
}

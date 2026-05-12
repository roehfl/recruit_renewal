package com.shinyoung.recruit.common.hash;

import java.security.MessageDigest;
import java.util.HexFormat;

public class HashUtil {
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid input for hashing", e);
        }
    }
}

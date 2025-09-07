package com.saferoom.crypto;

import java.security.SecureRandom;

public class VerificationCodeGenerator {

    public static String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        int length = 6; 

        while (sb.length() < length) {
                sb.append(random.nextInt(10));
        }

        return sb.toString();
    }
}

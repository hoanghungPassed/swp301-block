package com.fastfood.common.util;

import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;

public final class PasswordUtil {

    private static final int COST = 10;

    private static final int TEMP_LENGTH = 10;

    private PasswordUtil() {
    }

    public static String hash(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(COST));
    }

    public static String randomTemporary() {
        final String letters = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
        final String digits = "23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(TEMP_LENGTH);
        sb.append(letters.charAt(random.nextInt(letters.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        String all = letters + digits;
        while (sb.length() < TEMP_LENGTH) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        for (int i = sb.length() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = sb.charAt(i);
            sb.setCharAt(i, sb.charAt(j));
            sb.setCharAt(j, tmp);
        }
        return sb.toString();
    }

    public static boolean matches(String rawPassword, String hash) {
        if (rawPassword == null || hash == null || hash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

package com.garagepulse.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Simple salted SHA-256 password hashing.
 * Deliberately lightweight (built-in Java only, no external crypto library)
 * to keep the project's memory/CPU footprint small, while still never
 * storing plain-text passwords.
 *
 * Flow:
 *  Signup -> generate a random salt, hash(password + salt), store both.
 *  Login  -> fetch the user's stored salt, re-hash the submitted password
 *            with that same salt, compare to the stored hash.
 */
public class PasswordUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() { }

    /** Generates a random 16-byte salt, returned as a hex string. */
    public static String generateSalt() {
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        return toHex(saltBytes);
    }

    /** Hashes password+salt with SHA-256, returns the digest as a hex string. */
    public static String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes("UTF-8"));
            byte[] hashed = digest.digest(password.getBytes("UTF-8"));
            return toHex(hashed);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    /** Re-hashes the submitted password with the stored salt and compares. */
    public static boolean verify(String submittedPassword, String storedSalt, String storedHash) {
        String computedHash = hash(submittedPassword, storedSalt);
        return computedHash.equals(storedHash);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

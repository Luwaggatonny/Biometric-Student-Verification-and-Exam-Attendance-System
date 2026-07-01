package com.uict.bioverify.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordHasher {

    // Generate a secure random salt
    public static String generateSalt() {
        SecureRandom sr = new SecureRandom();
        byte[] salt = new byte[16];
        sr.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    // Hash password using SHA-256 and salt
    public static String hash(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Base64.getDecoder().decode(salt));
            byte[] hashedPassword = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hashedPassword);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    // Encrypt password by generating salt and hash, returning "salt:hash"
    public static String hashPassword(String password) {
        String salt = generateSalt();
        String hash = hash(password, salt);
        return salt + ":" + hash;
    }

    // Verify raw password against stored "salt:hash"
    public static boolean checkPassword(String password, String storedPasswordHash) {
        if (storedPasswordHash == null || !storedPasswordHash.contains(":")) {
            return false;
        }
        String[] parts = storedPasswordHash.split(":");
        if (parts.length != 2) return false;
        String salt = parts[0];
        String storedHash = parts[1];
        return hash(password, salt).equals(storedHash);
    }
}

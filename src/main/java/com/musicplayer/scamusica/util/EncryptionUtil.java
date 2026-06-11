package com.musicplayer.scamusica.util;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptionUtil {

    // ---- AES Key (16 bytes = 128-bit AES) ----
    private static final String SECRET = "SCA_MUSICA_AES16";

    private static SecretKeySpec getSecretKey() {
        return new SecretKeySpec(SECRET.getBytes(), "AES");
    }

    // ----------------------------------------------------
    // STRING ENCRYPTION (Your existing methods)
    // ----------------------------------------------------
    public static String encrypt(String plainText) {
        try {
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");

            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());

            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting token", e);
        }
    }

    public static String decrypt(String encryptedText) {
        try {
            SecretKeySpec secretKey = getSecretKey();
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");

            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText));

            return new String(decryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting token", e);
        }
    }

    // ----------------------------------------------------
    // FILE ENCRYPTION (NEW — SAFE FOR MP3)
    // AES/CBC with random IV stored in first 16 bytes
    // ----------------------------------------------------
    public static void encryptFile(File input, File output) throws Exception {
        SecretKeySpec key = getSecretKey();

        // Generate IV
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);

        try (FileOutputStream fos = new FileOutputStream(output);
             FileInputStream fis = new FileInputStream(input)) {

            fos.write(iv); // write IV to beginning

            try (CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    cos.write(buffer, 0, read);
                }
            }
        }
    }

    // ----------------------------------------------------
    // FILE DECRYPTION (NEW)
    // Reads IV from first 16 bytes
    // ----------------------------------------------------
    public static void decryptFile(File input, File output) throws Exception {
        SecretKeySpec key = getSecretKey();

        try (FileInputStream fis = new FileInputStream(input)) {

            byte[] iv = new byte[16];
            int ivRead = fis.read(iv);
            if (ivRead != 16) throw new RuntimeException("Invalid encrypted file format");

            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);

            try (CipherInputStream cis = new CipherInputStream(fis, cipher);
                 FileOutputStream fos = new FileOutputStream(output)) {

                byte[] buffer = new byte[4096];
                int read;
                while ((read = cis.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
            }
        }
    }
}

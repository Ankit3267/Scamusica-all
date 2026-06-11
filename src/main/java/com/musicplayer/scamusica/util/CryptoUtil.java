package com.musicplayer.scamusica.util;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;

public class CryptoUtil {

    private static final String ALGO = "AES";
    private static final byte[] KEY = "1234567890123456".getBytes();

    public static CipherOutputStream encrypt(OutputStream os) throws Exception {
        SecretKeySpec key = new SecretKeySpec(KEY, ALGO);
        Cipher cipher = Cipher.getInstance(ALGO);

        cipher.init(Cipher.ENCRYPT_MODE, key);
        return new CipherOutputStream(os, cipher);
    }

    public static CipherInputStream decrypt(InputStream is) throws Exception {
        SecretKeySpec key = new SecretKeySpec(KEY, ALGO);
        Cipher cipher = Cipher.getInstance(ALGO);

        cipher.init(Cipher.DECRYPT_MODE, key);
        return new CipherInputStream(is, cipher);
    }

}

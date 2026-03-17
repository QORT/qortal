package org.qortal.test.utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Security;

public class TestUtils {
    public static byte[] generatePublicKey() throws Exception {
        // Add the Bouncy Castle provider
        Security.addProvider(new BouncyCastleProvider());

        // Generate a key pair
        KeyPair keyPair = generateKeyPair();

        // Get the public key
        PublicKey publicKey = keyPair.getPublic();

        // Get the public key as a byte array
        byte[] publicKeyBytes = publicKey.getEncoded();

        // Generate a RIPEMD160 message digest from the public key
        byte[] ripeMd160Digest = generateRipeMd160Digest(publicKeyBytes);

        return ripeMd160Digest;
    }

    public static KeyPair generateKeyPair() throws Exception {
        // Generate a key pair using the RSA algorithm
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048); // Key size (bits)
        return keyGen.generateKeyPair();
    }

    public static byte[] generateRipeMd160Digest(byte[] input) throws Exception {
        // Create a RIPEMD160 message digest instance
        MessageDigest ripeMd160 = MessageDigest.getInstance("RIPEMD160", new BouncyCastleProvider());

        // Update the message digest with the input bytes
        ripeMd160.update(input);

        // Get the message digest bytes
        return ripeMd160.digest();
    }
}

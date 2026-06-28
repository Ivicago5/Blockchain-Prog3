package Util;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class Crypto {

    // Took a lot of the code from the CryptoUtil you provided for us on Tutorials
    // just generalized it so it works with multiple wallets later on

    public static String SHA256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte B : hash){
                String hexadecimal = Integer.toHexString(0xff & B);
                if (hexadecimal.length() == 1) sb.append('0');
                sb.append(hexadecimal);
            }
            return sb.toString();
        } catch (Exception e) {
            Logger.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }
    // method for generating keys
    public static KeyPair generateECKeyPair() {
        try {
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(ecSpec, new SecureRandom());
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            Logger.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }
    // method for signing data
    public static String signECDSA(PrivateKey privateKey, String data){
        try {
            Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
            ecdsaSign.initSign(privateKey);
            ecdsaSign.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signature = ecdsaSign.sign();
            return Base64.getEncoder().encodeToString(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            Logger.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // method for verifying signature
    public static boolean verifyECDSA(String base64PublicKey, String data, String base64Signature) {
        try {
            PublicKey publicKey = publicKeyFromBase64(base64PublicKey);
            Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA");
            ecdsaVerify.initVerify(publicKey);
            ecdsaVerify.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] sigBytes = Base64.getDecoder().decode(base64Signature);
            return ecdsaVerify.verify(sigBytes);
        } catch (Exception e) {
            Logger.error(e.getMessage());
            return false;
        }
    }

    // encode/decode helpers

    public static String publicKeyToBase64 (PublicKey publicKey){
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    public static PublicKey publicKeyFromBase64 (String base64PublicKey) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64PublicKey);
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
            Logger.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public static String privateKeyToBase64 (PrivateKey privateKey){
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    public static PrivateKey privateKeyFromBase64(String base64PrivateKey){
        try {
            byte[] decoded = Base64.getDecoder().decode(base64PrivateKey);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            Logger.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

}

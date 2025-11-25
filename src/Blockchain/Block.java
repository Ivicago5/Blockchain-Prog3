package Blockchain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import Util.Logger;

public class Block {
    String data;
     int index;
    long timestamp;
    String previousHash;
    String hash;


    public Block(int index, String data, String previousHash) {
        this.index = index;
        this.data = data;
        this.previousHash = previousHash;
        this.timestamp = new Date().getTime();
        this.hash = calculateHash();
    }

    private String calculateHash() {
        String content_of_Block = index + data + timestamp + previousHash;
        return SHA256Hash(content_of_Block);
    }
    //making this a separate method, because I will probably need it later! JUST A GUESS
    public static String SHA256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
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
}
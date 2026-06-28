package Wallet;

import Blockchain.Blockchain;
import Transaction.Transaction;
import Transaction.TransactionInput;
import Transaction.UTXO;

import Util.Logger;
import Util.Crypto;

import java.util.ArrayList;
import java.util.List;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public class Wallet {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public Wallet() {
        KeyPair kp = Crypto.generateECKeyPair();
        this.privateKey = kp.getPrivate();
        this.publicKey = kp.getPublic();
    }

    public Wallet(String publicKeyBase64, String privateKeyBase64){
        this.publicKey = Crypto.publicKeyFromBase64(publicKeyBase64);
        this.privateKey = Crypto.privateKeyFromBase64(privateKeyBase64);
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getPublicKeyBase64() {
        return Crypto.publicKeyToBase64(publicKey);
    }

    public int getBalance(Blockchain blockchain){
        return blockchain.getBalance(getPublicKeyBase64());
    }

    public Transaction createTransaction(String receiver, int amount, Blockchain blockchain){

        if (receiver == null || receiver.isEmpty()) {
            Logger.error("Wallet: receiver can not be null or empty!");
            return null;
        }

        if (amount <= 0) {
            Logger.error("Wallet: amount must be positive!");
            return null;
        }

        List<UTXO> ownedUTXOs = new ArrayList<>();
        int total = 0;

        for (UTXO utxo : blockchain.getUnspentOutputs(getPublicKeyBase64())) {
            ownedUTXOs.add(utxo);
            total += utxo.getAmount();

            if (total >= amount) break;
        }

        if (total < amount){
            Logger.error("Wallet: Insufficient funds");
            return null;
        }

        List<TransactionInput> inputs = new ArrayList<>();

        for (UTXO utxo : ownedUTXOs){
            inputs.add(new TransactionInput(utxo.getId()));
        }

        Transaction tx = new Transaction(
                getPublicKeyBase64(),
                receiver,
                amount,
                inputs
        );

        tx.sign(privateKey);

        return tx;
    }

}




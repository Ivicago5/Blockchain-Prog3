package Blockchain;

import Transaction.Transaction;

import Util.Crypto;
import Util.Logger;

import java.util.ArrayList;
import java.util.List;

public class Block {

    private final int index;
    private final long timestamp;
    private final String previousHash;
    private String hash;
    private int nonce;
    private final List<Transaction> transactions;

    public Block(int index, List<Transaction> transactions , String previousHash) {
        this(index, transactions, previousHash, System.currentTimeMillis());
    }

    public Block(int index, List<Transaction> transactions , String previousHash, long timestamp) {
        this.index = index;
        this.previousHash = previousHash;
        this.timestamp = timestamp;

        if (transactions == null){
            this.transactions = new ArrayList<>();  // no null values. Empty is full fine
        } else {
            this.transactions = new ArrayList<>(transactions);
        }

        this.nonce = 0;
        this.hash = calculateHash();
    }

    public String calculateHash() {
        StringBuilder transactionData = new StringBuilder();
        for (Transaction tx : transactions){
            transactionData.append(tx.getTxId());
        }

        String content_of_Block = index + transactionData.toString() + timestamp + previousHash + nonce;
        return Crypto.SHA256Hash(content_of_Block);

    }

    public void mineBlock(int difficulty) {
        String target = "0".repeat(difficulty);

        while (!calculateHash().substring(0, difficulty).equals(target)) {
            nonce++;
        }

        hash = calculateHash();
        Logger.info("Block #" + index + " mined! Nonce: " + nonce + " Hash: " + hash);

    }

    public List<Transaction> getTransactions() {
        return new ArrayList<>(transactions);
    }

    public int getNonce() {
        return nonce;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getIndex() {
        return index;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getHash() {
        return hash;
    }
}
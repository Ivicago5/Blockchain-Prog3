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
    // netowrking constructor, keeps nonce and hash (does not get recalculated and reset)
    public Block(int index, List<Transaction> transactions, String previousHash, long timestamp, int nonce, String hash) {
        this.index = index;
        this.previousHash = previousHash;
        this.timestamp = timestamp;

        if (transactions == null) {
            this.transactions = new ArrayList<>();
        } else {
            this.transactions = new ArrayList<>(transactions);
        }

        this.nonce = nonce;
        this.hash = hash;
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

    public String toJson() {

        StringBuilder json = new StringBuilder();

        json.append("{");

        json.append("\"index\":")
                .append(index)
                .append(",");

        json.append("\"timestamp\":")
                .append(timestamp)
                .append(",");

        json.append("\"previousHash\":\"")
                .append(Util.JsonUtil.escape(previousHash))
                .append("\",");

        json.append("\"hash\":\"")
                .append(Util.JsonUtil.escape(hash))
                .append("\",");

        json.append("\"nonce\":")
                .append(nonce)
                .append(",");

        json.append("\"transactions\":[");

        for (int i = 0; i < transactions.size(); i++) {

            json.append(transactions.get(i).toJson());

            if (i < transactions.size() - 1) {
                json.append(",");
            }
        }

        json.append("]");
        json.append("}");

        return json.toString();
    }

    public static Block fromJson(String json) {

        int index = Util.JsonUtil.extractInt(json, "index");

        long timestamp = Util.JsonUtil.extractLong(json, "timestamp");

        String previousHash =
                Util.JsonUtil.extractString(json, "previousHash");

        String hash =
                Util.JsonUtil.extractString(json, "hash");

        int nonce =
                Util.JsonUtil.extractInt(json, "nonce");

        String transactionsJson =
                Util.JsonUtil.extractArray(json, "transactions");

        List<Transaction> transactions = new ArrayList<>();

        for (String txJson :
                Util.JsonUtil.splitJsonArray(transactionsJson)) {

            transactions.add(
                    Transaction.fromJson(txJson)
            );
        }

        return new Block(
                index,
                transactions,
                previousHash,
                timestamp,
                nonce,
                hash
        );
    }
}
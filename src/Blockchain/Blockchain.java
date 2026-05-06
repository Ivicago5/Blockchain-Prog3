package Blockchain;

import Transaction.Transaction;
import Transaction.TransactionInput;
import Transaction.UTXO;
import Util.Logger;


import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class Blockchain {
    private final ArrayList<Block> chain;
    private final int difficulty = 6;
    private final List<Transaction> pendingTransactions;
    private final UTXOPool utxoPool;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public Blockchain(String genesisPublicKey) {
        chain = new ArrayList<>();
        utxoPool = new UTXOPool();
        pendingTransactions = new ArrayList<>();
        chain.add(createGenesisBlock(genesisPublicKey));

    }
    // genesis block is the first block in a chain, it has index 0 and previous hash is "0" ofc.
    private Block createGenesisBlock(String initialOwner) {

        List<Transaction> genesisTxs = new ArrayList<>();

        Transaction genesisTx = new Transaction(
                "SYSTEM",
                initialOwner,
                100,
                new ArrayList<>()
        );

        genesisTxs.add(genesisTx);

        Block genesis = new Block(0, genesisTxs, "0");

        UTXO utxo = new UTXO(
                genesisTx.getTxId() + "_0",
                initialOwner,
                100,
                genesisTx.getTxId()
        );

        utxoPool.addUTXO(utxo);

        return genesis;
    }

    public UTXOPool getUtxoPool() {
        return utxoPool;
    }

    public int getBalance(String publicKey){
        return utxoPool.getBalance(publicKey);
    }

    // going to need prev block for adding new blocks to get its hash
    public Block getLatestBlock(){
        return chain.getLast();
    }

    public void minePendingTransactions(){
        lock.writeLock().lock();

        try {
            if (pendingTransactions.isEmpty()) {
                Logger.warn("No transactions to mine");
                return;
            }

            List<Transaction> validTransactions = new ArrayList<>();

            for (Transaction tx : pendingTransactions) {
                if (validateTransaction(tx)) {
                    applyTransaction(tx);
                    validTransactions.add(tx);
                } else {
                    Logger.warn("Transaction Rejected!");
                }
            }

            if (validTransactions.isEmpty()) {
                Logger.warn("No valid transactions to mine");
                return;
            }

            Block prevBlock = getLatestBlock();

            Block newBlock = new Block(
                    chain.size(),
                    validTransactions,
                    prevBlock.getHash()
            );

            newBlock.mineBlock(difficulty);
            chain.add(newBlock);

            pendingTransactions.clear();

        } finally {
            lock.writeLock().unlock();
        }

    }

    public boolean isValid() {
        for (int i = 1; i < chain.size(); i++){ // starting from 1 cuz skipping the genesis block
            Block current = chain.get(i);
            Block previous = chain.get(i - 1);

            // has the block been tampered with?
            if (!current.getHash().equals(current.calculateHash())){
                Logger.error("Block " + i + " has been tampered!");
                return false;
            }
            // does the block link to previous block?
            if (!current.getPreviousHash().equals(previous.getHash())){
                Logger.error("Block " + i + " chain link is broken!");
                return false;
            }

            //Does it meet the difficulty requirements?
            String target = "0".repeat(difficulty);
            if (!current.getHash().substring(0, difficulty).equals(target)){
                Logger.error("Block " + i + " is not mined properly");
                return false;
            }
        }
        return true;
    }

    public void addTransaction(Transaction transaction){
        if (transaction == null){
            throw new IllegalArgumentException("Transaction cant be null");
        }
        pendingTransactions.add(transaction);
    }

    private boolean validateTransaction (Transaction tx) {
        if (tx == null) {
            Logger.error("Transaction is null");
            return false;
        }

        if (!tx.isSignatureValid()) {
            Logger.error("Signature is not valid");
            return false;
        }

        int totalInput = 0;

        for (TransactionInput input : tx.getInputs()) {
            UTXO utxo = utxoPool.getUTXO(input.getUtxoId());

            if (utxo == null){
                Logger.error("Referenced UTXO not found");
                return false;
            }

            if (!utxo.getOwner().equals(tx.getSenderPubKey())) {
                Logger.error("UTXO not owned by sender");
                return false;
            }

            totalInput += utxo.getAmount();
         }

        if (totalInput < tx.getAmount()) {
            Logger.error("Insufficient funds");
            return false;
        }

        if (tx.getInputs().isEmpty()) {
            Logger.error("Transaction has no inputs");
            return false;
        }

        if (tx.getAmount() <= 0) {
            Logger.error("Invalid transaction amount");
            return false;
        }

        if (tx.getReceiverPubKey() == null) {
            Logger.error("Receiver is null");
            return false;
        }

        Set<String> seen = new HashSet<>();

        for (TransactionInput input : tx.getInputs()){
            if (!seen.add(input.getUtxoId())){
                Logger.error("Duplicate input in transaction");
                return false;
            }
        }

        return true;

    }

    private void applyTransaction (Transaction tx) {
        int totalInput = 0;

        for (TransactionInput input : tx.getInputs()) {
            UTXO utxo = utxoPool.getUTXO(input.getUtxoId());

            if (!utxoPool.contains(input.getUtxoId())){
                throw new RuntimeException("UTXO disappeared during apply");
            }

            if (utxo == null){
                throw new RuntimeException("UTXO missing during apply");
            }
            totalInput += utxo.getAmount();
            utxoPool.removeUTXO(input.getUtxoId());
        }

        UTXO receiverUTXO = new UTXO(
                tx.getTxId() + "_0",
                tx.getReceiverPubKey(),
                tx.getAmount(),
                tx.getTxId()
        );

        utxoPool.addUTXO(receiverUTXO);

        int change = totalInput - tx.getAmount();

        if (change > 0) {
            UTXO changeUTXO = new UTXO(
                    tx.getTxId() + "_1",
                    tx.getSenderPubKey(),
                    change,
                    tx.getTxId()
            );
            utxoPool.addUTXO(changeUTXO);
        }
    }

    //DEBUGGING ONLY
    public void printChain() {
        Logger.info("=========== Printing whole Blockchain ===========");
        for (Block block : chain) {
            Logger.info("Block #" + block.getIndex());
            Logger.info("Timestamp: " + block.getTimestamp());
            Logger.info("Previous Hash: " + block.getPreviousHash());
            Logger.info("Hash: " + block.getHash());
            Logger.info("Nonce: " + block.getNonce());

            if (block.getTransactions().isEmpty()) {
                Logger.info("Transactions: []");
            } else {
                Logger.info("Transactions:");
                for (Transaction tx : block.getTransactions()) {
                    Logger.info("  " + tx.toDebugString());
                }
            }
            Logger.info("=====================================");
        }
    }

    public Block getBlock(int index){
        if (index >= 0 && index < chain.size()){
            return chain.get(index);
        }
        return null;
    }

}

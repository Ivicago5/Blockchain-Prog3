package Blockchain;

import Transaction.Transaction;
import Transaction.TransactionInput;
import Transaction.UTXO;
import Util.Logger;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantReadWriteLock;



public class Blockchain {
    private final ArrayList<Block> chain;
    private final int difficulty;
    private final Queue<Transaction> pendingTransactions;
    private final Set<String> pendingTransactionIds;
    private final UTXOPool utxoPool;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public Blockchain() {
        chain = new ArrayList<>();
        utxoPool = new UTXOPool();
        pendingTransactions = new ConcurrentLinkedQueue<>();
        pendingTransactionIds = ConcurrentHashMap.newKeySet();
        difficulty = Integer.parseInt(System.getenv().getOrDefault("DIFFICULTY", "4"));
        chain.add(createGenesisBlock());

    }
    // genesis block is the first block in a chain, it has index 0 and previous hash is "0" ofc.
    private Block createGenesisBlock() {

        List<Transaction> genesisTxs = new ArrayList<>();

        if (GenesisConfig.FAUCET_PUBLIC_KEY_BASE64 == null || GenesisConfig.FAUCET_PUBLIC_KEY_BASE64.isEmpty()) {
            throw new IllegalStateException("FAUCET_PUBLIC_KEY_BASE64 is missing");
        }

        Transaction genesisTx = new Transaction(
                "GENESIS",
                GenesisConfig.SYSTEM_SENDER,
                GenesisConfig.FAUCET_PUBLIC_KEY_BASE64,
                GenesisConfig.GENESIS_AMOUNT,
                new ArrayList<>()
        );

        genesisTxs.add(genesisTx);

        Block genesis = new Block(
                0,
                genesisTxs,
                GenesisConfig.GENESIS_PREVIOUS_HASH,
                GenesisConfig.GENESIS_TIMESTAMP
        );

        UTXO genesisUTXO = new UTXO(
                genesisTx.getTxId() + "_0",
                GenesisConfig.FAUCET_PUBLIC_KEY_BASE64,
                GenesisConfig.GENESIS_AMOUNT,
                genesisTx.getTxId()
        );

        utxoPool.addUTXO(genesisUTXO);

        return genesis;
    }


    public int getBalance(String publicKey){
        lock.readLock().lock();
        try {
            return utxoPool.getBalance(publicKey);
        } finally {
            lock.readLock().unlock();
        }

    }

    // going to need prev block for adding new blocks to get its hash
    public Block getLatestBlock(){
        lock.readLock().lock();
        try {
            return chain.getLast();
        } finally {
            lock.readLock().unlock();
        }

    }

    public List<UTXO> getUnspentOutputs(String publicKey) {
        lock.readLock().lock();
        try {
            List<UTXO> result = new ArrayList<>();

            for (UTXO utxo : utxoPool.getAllUTXOs()){
                if (utxo.getOwner().equals(publicKey)){
                    result.add(utxo);
                }
            }
            return result;
        } finally {
          lock.readLock().unlock();
        }
    }

    public int getPendingTransactionCount() {
        return pendingTransactionIds.size();
    }

    public List<String> getPendingTransactionIds() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(pendingTransactionIds);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Block minePendingTransactions() {
        lock.writeLock().lock();
        try {
            List<Transaction> toProcess = new ArrayList<>();

            Transaction tx;

            //transaction si no longer pending, even if it fails during final validation
            while ((tx = pendingTransactions.poll()) != null) {
                pendingTransactionIds.remove(tx.getTxId());
                toProcess.add(tx);
            }

            if (toProcess.isEmpty()) {
                Logger.warn("No transactions to mine");
                return null;
            }

            List<Transaction> validTransactions = new ArrayList<>();

            for (Transaction transaction : toProcess) {
                if (validateAndApplyTransaction(transaction)) {
                    validTransactions.add(transaction);
                } else {
                    Logger.warn(
                            "Transaction rejected during mining: "
                                    + transaction.getTxId()
                    );
                }
            }

            if (validTransactions.isEmpty()) {
                Logger.warn("No valid transactions to mine");
                return null;
            }

            Block prevBlock = chain.getLast();

            Block newBlock = new Block(
                    prevBlock.getIndex() + 1,
                    validTransactions,
                    prevBlock.getHash()
            );

            newBlock.mineBlock(difficulty);
            chain.add(newBlock);

            return newBlock;

        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isValid() {
        lock.readLock().lock();

        try {
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

        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean addTransaction(Transaction tx) {
        lock.readLock().lock();

        try {
            if (tx == null) {
                Logger.warn("Cannot add null transaction");
                return false;
            }

            if (tx.getTxId() == null || tx.getTxId().isEmpty()) {
                Logger.warn("Cannot add transaction without transaction ID");
                return false;
            }

            if (tx.isGenesis()) {
                Logger.warn("Genesis transactions cannot be added after blockchain creation");
                return false;
            }

            if (!pendingTransactionIds.add(tx.getTxId())) {
                Logger.warn(
                        "Transaction already exists in pending transactions: "
                                + tx.getTxId()
                );
                return false;
            }


            // validate against a temporary UTXO snapshot.

            if (!validateTransaction(tx)) {
                pendingTransactionIds.remove(tx.getTxId());
                Logger.warn("Transaction rejected before entering pending queue");
                return false;
            }

            boolean queued = pendingTransactions.offer(tx);

            if (!queued) {
                pendingTransactionIds.remove(tx.getTxId());
                Logger.warn("Failed to add transaction to pending queue");
                return false;
            }

            Logger.info("Transaction added to pending queue: " + tx.getTxId());
            return true;

        } finally {
            lock.readLock().unlock();
        }
    }

    private void removeConfirmedPendingTransactions(Block block) {
        if (block == null || block.getTransactions().isEmpty()) {
            return;
        }

        Set<String> confirmedIds = new HashSet<>();

        for (Transaction tx : block.getTransactions()) {
            if (tx != null && tx.getTxId() != null) {
                confirmedIds.add(tx.getTxId());
            }
        }

        if (confirmedIds.isEmpty()) {
            return;
        }

        int beforeRemoval = pendingTransactionIds.size();

        pendingTransactions.removeIf(
                pendingTx -> pendingTx != null && confirmedIds.contains(pendingTx.getTxId()));

        pendingTransactionIds.removeAll(confirmedIds);

        int removed = beforeRemoval - pendingTransactionIds.size();

        Logger.info("Removed " + removed + " confirmed transaction(s) from mempool");
    }

    private boolean validateTransaction(Transaction tx) {
        UTXOPool temporaryPool = utxoPool.copy();
        return validateAndApplyTransaction(tx, temporaryPool);
    }

    private boolean validateAndApplyTransaction(Transaction tx, UTXOPool workingPool) {
        if (tx == null) {
            Logger.error("Transaction is null");
            return false;
        }

        if (tx.isSystemTransaction()) {

            if (tx.isGenesis()) {
                return false;
            }

            if (tx.isFaucetTransaction() || tx.isMiningReward()) {

                UTXO output = new UTXO(
                        tx.getTxId()+"_0",
                        tx.getReceiverPubKey(),
                        tx.getAmount(),
                        tx.getTxId()
                );

                workingPool.addUTXO(output);

                return true;
            }

            return false;
        }

        if (tx.getAmount() <= 0) {
            Logger.error("Invalid transaction amount");
            return false;
        }

        if (tx.getReceiverPubKey() == null || tx.getReceiverPubKey().isEmpty()) {
            Logger.error("Receiver is null or empty");
            return false;
        }

        if (tx.getInputs().isEmpty()) {
            Logger.error("Transaction has no inputs");
            return false;
        }

        if (!tx.isSignatureValid()) {
            Logger.error("Signature is not valid");
            return false;
        }

        int totalInput = 0;
        Set<String> seen = new HashSet<>();
        List<UTXO> spentUTXOs = new ArrayList<>();

        for (TransactionInput input : tx.getInputs()) {
            if (input == null || input.getUtxoId() == null || input.getUtxoId().isEmpty()) {
                Logger.error("Invalid transaction input");
                return false;
            }

            if (!seen.add(input.getUtxoId())) {
                Logger.error("Duplicate input in transaction");
                return false;
            }

            UTXO utxo = workingPool.getUTXO(input.getUtxoId());

            if (utxo == null) {
                Logger.warn("Referenced UTXO not found or already spent");
                return false;
            }

            if (!utxo.getOwner().equals(tx.getSenderPubKey())) {
                Logger.error("UTXO not owned by sender");
                return false;
            }

            totalInput += utxo.getAmount();
            spentUTXOs.add(utxo);
        }

        if (totalInput < tx.getAmount()) {
            Logger.error("Insufficient funds");
            return false;
        }

        for (UTXO utxo : spentUTXOs) {
            workingPool.removeUTXO(utxo.getId());
        }

        UTXO receiverUTXO = new UTXO(
                tx.getTxId() + "_0",
                tx.getReceiverPubKey(),
                tx.getAmount(),
                tx.getTxId()
        );

        workingPool.addUTXO(receiverUTXO);

        int change = totalInput - tx.getAmount();

        if (change > 0) {
            UTXO changeUTXO = new UTXO(
                    tx.getTxId() + "_1",
                    tx.getSenderPubKey(),
                    change,
                    tx.getTxId()
            );

            workingPool.addUTXO(changeUTXO);
        }

        return true;
    }

    private boolean validateAndApplyTransaction(Transaction tx) {
        return validateAndApplyTransaction(tx, utxoPool);
    }

    public boolean receiveBlock(Block block){
        lock.writeLock().lock();
        try {
            if (block == null) {
                Logger.warn("Received a null block");
                return false;
            }

            Block latestBlock = getLatestBlock();

            if (block.getIndex() != latestBlock.getIndex() + 1) {
                Logger.warn("Received block with invalid index");
                return false;
            }

            if (!block.getPreviousHash().equals(latestBlock.getHash())) {
                Logger.warn("Received block does not connect to current chain tip");
                return false;
            }

            if (!block.calculateHash().equals(block.getHash())) {
                Logger.warn("Received block has invalid hash");
                return false;
            }

            String target = "0".repeat(difficulty);

            if (!block.getHash().startsWith(target)) {
                Logger.warn("Received block does not satisfy proof of work");
                return false;
            }

            UTXOPool temporaryPool = utxoPool.copy();

            for (Transaction tx : block.getTransactions()) {
                if (!validateAndApplyTransaction(tx, temporaryPool)) {
                    Logger.warn("Received block rejected because transaction validation failed");
                    return false;
                }
            }

            utxoPool.replaceWith(temporaryPool);
            chain.add(block);

            removeConfirmedPendingTransactions(block);

            Logger.info("Received and accepted external block number  " + block.getIndex());

            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }


    //DEBUGGING ONLY
    /*
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
    */

    public int getHeight() {
        lock.readLock().lock();
        try {
            return chain.size() - 1;
        } finally {
            lock.readLock().unlock();
        }
    }

    public String getLatestHash() {
        lock.readLock().lock();
        try {
            return chain.get(chain.size() - 1).getHash();
        } finally {
            lock.readLock().unlock();
        }
    }

    public Block getBlock(int index){
        lock.readLock().lock();
        try {
            if (index >= 0 && index < chain.size()){
                return chain.get(index);
            }

            return null;
        } finally {
            lock.readLock().unlock();
        }

    }

}

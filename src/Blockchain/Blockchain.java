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

    private Blockchain(boolean skipGenesis) {

        chain = new ArrayList<>();
        utxoPool = new UTXOPool();
        pendingTransactions = new ConcurrentLinkedQueue<>();
        pendingTransactionIds = ConcurrentHashMap.newKeySet();
        difficulty = GenesisConfig.BLOCK_DIFFICULTY;
        if (!skipGenesis) {
            chain.add(createGenesisBlock());
        }
    }

    public Blockchain() {
        this(false);
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
                new ArrayList<>(),
                "GENESIS_BLOCK"
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

    private void resetState() {
        chain.clear();
        utxoPool.clear();
        pendingTransactions.clear();
        pendingTransactionIds.clear();
    }

    public Block minePendingTransactions(String minerPublicKey) {
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

            UTXOPool temporaryPool = utxoPool.copy();

            for (Transaction transaction : toProcess) {

                if (validateTransaction(transaction, temporaryPool)) {

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

            String rewardID = "BLOCK_" + (prevBlock.getIndex() + 1);

            Transaction rewardTransaction = new Transaction(
                    "MINING_REWARD",
                    GenesisConfig.SYSTEM_SENDER,
                    minerPublicKey,
                    GenesisConfig.MINING_REWARD,
                    new ArrayList<>(),
                    rewardID
            );

            validTransactions.add(rewardTransaction);

            Logger.info("Mining reward added for miner.");

            Logger.info(
                    "Mining block with "
                            + validTransactions.size()
                            + " transaction(s)."
            );



            Block newBlock = new Block(
                    prevBlock.getIndex() + 1,
                    validTransactions,
                    prevBlock.getHash()
            );

            newBlock.mineBlock(difficulty);
            Logger.info(
                    "Finished mining block #" + newBlock.getIndex()
            );

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
        return validateTransaction(tx, temporaryPool);
    }

    private boolean validateTransaction(Transaction tx, UTXOPool workingPool) {
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

    private void applyTransaction(Transaction tx) {

        // System transactions (FAUCET / MINING_REWARD)
        if (tx.isFaucetTransaction() || tx.isMiningReward()) {

            utxoPool.addUTXO(
                    new UTXO(
                            tx.getTxId() + "_0",
                            tx.getReceiverPubKey(),
                            tx.getAmount(),
                            tx.getTxId()
                    )
            );

            return;
        }

        int totalInput = 0;

        // Calculate total input BEFORE removing UTXOs
        for (TransactionInput input : tx.getInputs()) {

            UTXO utxo = utxoPool.getUTXO(input.getUtxoId());

            if (utxo != null) {
                totalInput += utxo.getAmount();
            }
        }

        // Spend inputs
        for (TransactionInput input : tx.getInputs()) {
            utxoPool.removeUTXO(input.getUtxoId());
        }

        // Receiver output
        utxoPool.addUTXO(
                new UTXO(
                        tx.getTxId() + "_0",
                        tx.getReceiverPubKey(),
                        tx.getAmount(),
                        tx.getTxId()
                )
        );

        // Change output
        int change = totalInput - tx.getAmount();

        if (change > 0) {

            utxoPool.addUTXO(
                    new UTXO(
                            tx.getTxId() + "_1",
                            tx.getSenderPubKey(),
                            change,
                            tx.getTxId()
                    )
            );
        }
    }


    public boolean acceptBlock(Block block){
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
                if (!validateTransaction(tx, temporaryPool)) {
                    Logger.warn("Received block rejected because transaction validation failed");
                    return false;
                }
            }

            for (Transaction tx : block.getTransactions()) {
                applyTransaction(tx);
            }
            chain.add(block);
            Logger.info("Chain height is now " + getHeight());

            if (!pendingTransactions.isEmpty()){
                removeConfirmedPendingTransactions(block);
            }

            Logger.info("Received and accepted external block number  " + block.getIndex());

            return true;

        } finally {
            lock.writeLock().unlock();
        }
    }

    public String toJson() {

        StringBuilder json = new StringBuilder();

        json.append("{");

        json.append("\"height\":")
                .append(getHeight())
                .append(",");

        json.append("\"difficulty\":")
                .append(difficulty)
                .append(",");

        json.append("\"blocks\":[");

        for (int i = 0; i < chain.size(); i++) {

            json.append(chain.get(i).toJson());

            if (i < chain.size() - 1) {
                json.append(",");
            }
        }

        json.append("]}");

        return json.toString();
    }

    public boolean replaceChain(List<Block> newChain) {

        lock.writeLock().lock();

        try {

            if (newChain == null || newChain.isEmpty()) {
                Logger.warn("Received empty chain");
                return false;
            }

            if (newChain.size() <= chain.size()) {
                Logger.info("Received chain is not longer than local chain");
                return false;
            }

            Blockchain candidate = new Blockchain(true);

            candidate.chain.add(newChain.getFirst());

            Transaction genesisTx = newChain.getFirst().getTransactions().getFirst();

            candidate.utxoPool.addUTXO(
                    new UTXO(
                            genesisTx.getTxId() + "_0",
                            GenesisConfig.FAUCET_PUBLIC_KEY_BASE64,
                            GenesisConfig.GENESIS_AMOUNT,
                            genesisTx.getTxId()
                    )
            );

            for (int i = 1; i < newChain.size(); i++) {

                if (!candidate.acceptBlock(newChain.get(i))) {

                    Logger.warn("Replacement chain is invalid");

                    return false;
                }
            }

            resetState();

            chain.addAll(candidate.chain);

            utxoPool.replaceWith(candidate.utxoPool);

            Logger.info(
                    "Local blockchain replaced with longer valid blockchain. New height: "
                            + getHeight()
            );

            return true;

        } finally {

            lock.writeLock().unlock();
        }
    }

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

    public List<Block> getBlocks() {

        lock.readLock().lock();

        try {
            return new ArrayList<>(chain);
        }
        finally {
            lock.readLock().unlock();
        }
    }

}

package Blockchain;

import Transaction.UTXO;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UTXOPool {
    private final Map<String, UTXO> utxos;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public UTXOPool() {
        this.utxos = new HashMap<>();
    }

    public UTXO getUTXO (String id) {
        lock.readLock().lock();
        try {
            return utxos.get(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void addUTXO (UTXO utxo){
        lock.writeLock().lock();
        try {
            utxos.put(utxo.getId(), utxo);
        } finally {
            lock.writeLock().unlock();
        }

    }

    public void removeUTXO (String id) {
        lock.writeLock().lock();
        try {
            utxos.remove(id);
        } finally {
            lock.writeLock().unlock();
        }

    }

    public Collection<UTXO> getAllUTXOs() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(utxos.values());
        } finally {
            lock.readLock().unlock();
        }

    }

    public int getBalance (String publickKey) {
        int balance = 0;

        for (UTXO utxo : utxos.values()) {
            if (utxo.getOwner().equals(publickKey)) {
                balance += utxo.getAmount();
            }
        }

        return balance;
    }

    public boolean contains (String id) {
        return utxos.containsKey(id);
    }

    public void clear() {
        utxos.clear();
    }

}

package Blockchain;

import Transaction.UTXO;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;

public class UTXOPool {
    private final Map<String, UTXO> utxos;

    public UTXOPool() {
        this.utxos = new HashMap<>();
    }

    public UTXO getUTXO (String id) {
        return utxos.get(id);
    }

    public void addUTXO (UTXO utxo){
        utxos.put(utxo.getId(), utxo);
    }

    public void removeUTXO (String id) {
        utxos.remove(id);
    }

    public Collection<UTXO> getAllUTXOs() {
        return new ArrayList<>(utxos.values());
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

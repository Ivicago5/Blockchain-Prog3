
import Blockchain.Blockchain;
import Transaction.Transaction;
import Transaction.TransactionInput;
import Transaction.UTXO;
import Util.Logger;
import Wallet.Wallet;

import java.nio.file.FileVisitOption;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        Logger.info("Starting Stuff...");


        Wallet ivica = new Wallet();
        Wallet anja = new Wallet();

        Blockchain bc = new Blockchain(ivica.getPublicKeyBase64());

        Logger.info("Initial Balances: ");
        Logger.info("ivica: " + ivica.getBalance(bc));
        Logger.info("anja: " + anja.getBalance(bc));

        Transaction tx1 = ivica.createTransaction(
                anja.getPublicKeyBase64(),
                10,
                bc
        );

        if (tx1 != null){
            bc.addTransaction(tx1);
            bc.minePendingTransactions();
        }

        Logger.info("After tx1: ");
        Logger.info("ivica: " + ivica.getBalance(bc));
        Logger.info("anja: " + anja.getBalance(bc));

        Transaction tx2 = ivica.createTransaction(
                anja.getPublicKeyBase64(),
                15,
                bc
        );

        if (tx2 != null){
            bc.addTransaction(tx2);
            bc.minePendingTransactions();
        }

        Logger.info("After tx2: ");
        Logger.info("ivica: " + ivica.getBalance(bc));
        Logger.info("anja: " + anja.getBalance(bc));

        // debug print
        bc.printChain();

        Logger.warn("Checking if blockchain is valid.   Answer " + bc.isValid());

        Logger.info("Genesis balance: " + bc.getBalance("GENESIS_WALLET"));

    }
}

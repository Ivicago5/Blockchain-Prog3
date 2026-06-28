
import Blockchain.Blockchain;
import Transaction.Transaction;
import Blockchain.GenesisConfig;
import Util.Logger;
import Wallet.Wallet;

public class Main {
    public static void main(String[] args) {
        Logger.info("Starting Stuff...");

        String faucetPrivateKey = System.getenv("FAUCET_PRIVATE_KEY_BASE64");

        Wallet faucet = new Wallet(
                GenesisConfig.FAUCET_PUBLIC_KEY_BASE64,
                faucetPrivateKey
        );

        Wallet ivica = new Wallet();
        Wallet anja = new Wallet();

        Blockchain bc = new Blockchain();

        Logger.info("Initial Balances: ");
        Logger.info("ivica: " + ivica.getBalance(bc));
        Logger.info("anja: " + anja.getBalance(bc));
        Logger.info("faucet: " + faucet.getBalance(bc));

        Transaction faucetToIvica = faucet.createTransaction(
                ivica.getPublicKeyBase64(),
                100,
                bc
        );

        if (faucetToIvica != null) {
            bc.addTransaction(faucetToIvica);
            bc.minePendingTransactions();
        }


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
        Logger.info("Faucet " + faucet.getBalance(bc));

        // debug print
        //bc.printChain();

        Logger.warn("Checking if blockchain is valid.   Answer " + bc.isValid());
    }
}

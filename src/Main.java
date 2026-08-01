
import Blockchain.Blockchain;
import Transaction.Transaction;
import Transaction.TransactionInput;
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
            bc.minePendingTransactions(faucet.getPublicKeyBase64());
        }


        Transaction tx1 = ivica.createTransaction(
                anja.getPublicKeyBase64(),
                10,
                bc
        );

        if (tx1 != null){
            bc.addTransaction(tx1);
            bc.minePendingTransactions(ivica.getPublicKeyBase64());
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
            bc.minePendingTransactions(anja.getPublicKeyBase64());
        }

        Logger.info("After tx2: ");
        Logger.info("ivica: " + ivica.getBalance(bc));
        Logger.info("anja: " + anja.getBalance(bc));
        Logger.info("Faucet " + faucet.getBalance(bc));

        // debug print
        //bc.printChain();

        String json = tx1.toJson();

        System.out.println(json);


        Transaction copy = Transaction.fromJson(json);


        System.out.println(copy.toDebugString());

        System.out.println(
                copy.isSignatureValid()
        );

        System.out.println(
                tx1.getTxId().equals(copy.getTxId())
        );

        Logger.warn("Checking if blockchain is valid.   Answer " + bc.isValid());

    }
}

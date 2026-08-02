import Blockchain.Block;
import Blockchain.Blockchain;
import Blockchain.GenesisConfig;
import Transaction.Transaction;
import Util.Logger;
import Wallet.Wallet;

public class Main {

    private static void printBalances(Blockchain blockchain,
                                      Wallet faucet,
                                      Wallet alice,
                                      Wallet bob,
                                      Wallet charlie) {

        System.out.println();
        System.out.println("============== BALANCES ==============");
        System.out.printf("Faucet  : %d%n", faucet.getBalance(blockchain));
        System.out.printf("Alice   : %d%n", alice.getBalance(blockchain));
        System.out.printf("Bob     : %d%n", bob.getBalance(blockchain));
        System.out.printf("Charlie : %d%n", charlie.getBalance(blockchain));
        System.out.println("======================================");
        System.out.println();
    }

    private static void mine(Blockchain blockchain, String miner) {

        Block block = blockchain.minePendingTransactions(miner);

        if (block == null) {
            Logger.warn("Nothing to mine.");
            return;
        }

        if (!blockchain.acceptBlock(block)) {
            Logger.error("Failed to accept freshly mined block.");
        }
    }

    public static void main(String[] args) {

        Logger.info("Demo");

        String faucetPrivateKey = "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCC4kYAxvBSf1oazMc0dNL+WwH7zanyInxGJKKT0kD21lg==";


        Wallet faucet = new Wallet(
                GenesisConfig.FAUCET_PUBLIC_KEY_BASE64,
                faucetPrivateKey
        );

        Wallet alice = new Wallet();
        Wallet bob = new Wallet();
        Wallet charlie = new Wallet();

        Blockchain blockchain = new Blockchain();

        Logger.info("Created Faucet, Alice, Bob and Charlie.");

        printBalances(blockchain, faucet, alice, bob, charlie);

        //----------------------------------------------------
        // Faucet -> Alice
        //----------------------------------------------------

        Logger.info("Faucet sends 100 coins to Alice.");

        Transaction tx1 = faucet.createTransaction(
                alice.getPublicKeyBase64(),
                100,
                blockchain
        );

        if (tx1 != null) {
            blockchain.addTransaction(tx1);
            mine(blockchain, alice.getPublicKeyBase64());
        }

        printBalances(blockchain, faucet, alice, bob, charlie);

        //----------------------------------------------------
        // Alice -> Bob
        //----------------------------------------------------

        Logger.info("Alice sends 40 coins to Bob.");

        Transaction tx2 = alice.createTransaction(
                bob.getPublicKeyBase64(),
                40,
                blockchain
        );

        if (tx2 != null) {
            blockchain.addTransaction(tx2);
            mine(blockchain, bob.getPublicKeyBase64());
        }

        printBalances(blockchain, faucet, alice, bob, charlie);

        //----------------------------------------------------
        // Bob -> Charlie
        //----------------------------------------------------

        Logger.info("Bob sends 15 coins to Charlie.");

        Transaction tx3 = bob.createTransaction(
                charlie.getPublicKeyBase64(),
                15,
                blockchain
        );

        if (tx3 != null) {
            blockchain.addTransaction(tx3);
            mine(blockchain, charlie.getPublicKeyBase64());
        }

        printBalances(blockchain, faucet, alice, bob, charlie);

        //----------------------------------------------------
        // Charlie -> Alice
        //----------------------------------------------------

        Logger.info("Charlie sends 5 coins back to Alice.");

        Transaction tx4 = charlie.createTransaction(
                alice.getPublicKeyBase64(),
                5,
                blockchain
        );

        if (tx4 != null) {
            blockchain.addTransaction(tx4);
            mine(blockchain, alice.getPublicKeyBase64());
        }

        printBalances(blockchain, faucet, alice, bob, charlie);

        //----------------------------------------------------
        // Invalid transaction
        //----------------------------------------------------

        Logger.info("Alice tries to send 10000 coins.");

        Transaction invalid = alice.createTransaction(
                bob.getPublicKeyBase64(),
                10000,
                blockchain
        );

        if (invalid == null) {
            Logger.info("Overspending correctly rejected.");
        }

        //----------------------------------------------------

        Logger.info("Blockchain height: " + blockchain.getHeight());

        Logger.info("Blockchain valid: " + blockchain.isValid());

    }
}
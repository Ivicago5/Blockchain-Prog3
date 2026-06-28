import Blockchain.Blockchain;
import Blockchain.GenesisConfig;
import Transaction.Transaction;
import Util.Logger;
import Wallet.Wallet;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ConcurrentTest {

    public static void main(String[] args) {
        Logger.info("Starting concurrent blockchain test...");

        String faucetPrivateKey = System.getenv("FAUCET_PRIVATE_KEY_BASE64");

        Wallet faucet = new Wallet(
                GenesisConfig.FAUCET_PUBLIC_KEY_BASE64,
                faucetPrivateKey
        );

        Wallet ivica = new Wallet();
        Wallet anja = new Wallet();
        Wallet marko = new Wallet();
        Wallet luka = new Wallet();

        Blockchain bc = new Blockchain();

        Logger.info("Initial balances:");
        printBalances(bc, ivica, anja, marko, luka, faucet);

        /*
         * STEP 1:
         * Distribute funds so multiple wallets can create valid transactions.
         */

        Transaction faucetToIvica = faucet.createTransaction(
                ivica.getPublicKeyBase64(),
                100,
                bc
        );

        if (faucetToIvica != null) {
            bc.addTransaction(faucetToIvica);
            bc.minePendingTransactions();
        }

        Transaction setupTx1 = ivica.createTransaction(
                anja.getPublicKeyBase64(),
                30,
                bc
        );

        if (setupTx1 != null) {
            bc.addTransaction(setupTx1);
            bc.minePendingTransactions();
        }

        Transaction setupTx2 = ivica.createTransaction(
                marko.getPublicKeyBase64(),
                30,
                bc
        );

        if (setupTx2 != null) {
            bc.addTransaction(setupTx2);
            bc.minePendingTransactions();
        }

        Logger.info("After setup distribution:");
        printBalances(bc, ivica, anja, marko, luka, faucet);

        /*
         * Expected before concurrency:
         *
         * Ivica: 40
         * Anja: 30
         * Marko: 30
         * Luka: 0
         * Total: 100
         */

        int threadCount = 5;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        AtomicInteger submittedTransactions = new AtomicInteger(0);
        AtomicInteger nullTransactions = new AtomicInteger(0);
        AtomicReference<Throwable> failure = new AtomicReference<>(null);

        CountDownLatch startLatch = new CountDownLatch(1);

        Runnable submitter1 = () -> {
            awaitStart(startLatch);

            try {
                for (int i = 0; i < 5; i++) {
                    Transaction tx = anja.createTransaction(
                            luka.getPublicKeyBase64(),
                            2,
                            bc
                    );

                    if (tx != null) {
                        bc.addTransaction(tx);
                        submittedTransactions.incrementAndGet();
                    } else {
                        nullTransactions.incrementAndGet();
                    }

                    sleep(100);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };

        Runnable submitter2 = () -> {
            awaitStart(startLatch);

            try {
                for (int i = 0; i < 5; i++) {
                    Transaction tx = marko.createTransaction(
                            luka.getPublicKeyBase64(),
                            3,
                            bc
                    );

                    if (tx != null) {
                        bc.addTransaction(tx);
                        submittedTransactions.incrementAndGet();
                    } else {
                        nullTransactions.incrementAndGet();
                    }

                    sleep(120);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };

        Runnable submitter3 = () -> {
            awaitStart(startLatch);

            try {
                for (int i = 0; i < 5; i++) {
                    Transaction tx = ivica.createTransaction(
                            luka.getPublicKeyBase64(),
                            1,
                            bc
                    );

                    if (tx != null) {
                        bc.addTransaction(tx);
                        submittedTransactions.incrementAndGet();
                    } else {
                        nullTransactions.incrementAndGet();
                    }

                    sleep(140);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };

        Runnable miner = () -> {
            awaitStart(startLatch);

            try {
                for (int i = 0; i < 8; i++) {
                    bc.minePendingTransactions();
                    sleep(300);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };

        Runnable balanceReader = () -> {
            awaitStart(startLatch);

            try {
                for (int i = 0; i < 10; i++) {
                    int total = totalBalance(bc, ivica, anja, marko, luka, faucet);

                    Logger.info("Balance reader total supply: " + total);

                    if (total != 1000) {
                        throw new IllegalStateException(
                                "Total supply changed! Expected 1000 but got " + total
                        );
                    }

                    sleep(150);
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };

        executor.submit(submitter1);
        executor.submit(submitter2);
        executor.submit(submitter3);
        executor.submit(miner);
        executor.submit(balanceReader);

        Logger.info("Starting all threads...");
        startLatch.countDown();

        executor.shutdown();

        try {
            boolean finished = executor.awaitTermination(2, TimeUnit.MINUTES);

            if (!finished) {
                executor.shutdownNow();
                throw new RuntimeException("Concurrent test timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Concurrent test interrupted", e);
        }

        /*
         * Final mining pass.
         * This catches transactions that were submitted near the end.
         */
        bc.minePendingTransactions();

        Throwable error = failure.get();

        if (error != null) {
            Logger.error("Concurrent test failed: " + error.getMessage());
            error.printStackTrace();
            return;
        }

        Logger.info("Final balances:");
        printBalances(bc, ivica, anja, marko, luka, faucet);

        int finalTotal = totalBalance(bc, ivica, anja, marko, luka, faucet);

        Logger.info("Submitted transactions: " + submittedTransactions.get());
        Logger.info("Null transactions: " + nullTransactions.get());
        Logger.info("Final total supply: " + finalTotal);

        if (finalTotal != 1000) {
            Logger.error("TEST FAILED: total supply changed!");
        } else if (!bc.isValid()) {
            Logger.error("TEST FAILED: blockchain is invalid!");
        } else {
            Logger.info("TEST PASSED: blockchain survived concurrent execution.");
        }

        //bc.printChain();
    }

    private static void printBalances(
            Blockchain bc,
            Wallet ivica,
            Wallet anja,
            Wallet marko,
            Wallet luka,
            Wallet faucet
    ) {
        Logger.info("Ivica: " + ivica.getBalance(bc));
        Logger.info("Anja: " + anja.getBalance(bc));
        Logger.info("Marko: " + marko.getBalance(bc));
        Logger.info("Luka: " + luka.getBalance(bc));
        Logger.info("Faucet " + faucet.getBalance(bc));
        Logger.info("Total: " + totalBalance(bc, ivica, anja, marko, luka, faucet));
    }

    private static int totalBalance(
            Blockchain bc,
            Wallet ivica,
            Wallet anja,
            Wallet marko,
            Wallet luka,
            Wallet faucet
    ) {
        return ivica.getBalance(bc)
                + anja.getBalance(bc)
                + marko.getBalance(bc)
                + luka.getBalance(bc)
                + faucet.getBalance(bc);
    }

    private static void awaitStart(CountDownLatch startLatch) {
        try {
            startLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting to start", e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted during sleep", e);
        }
    }
}
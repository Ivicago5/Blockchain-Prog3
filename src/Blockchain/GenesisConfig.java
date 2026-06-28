package Blockchain;

public class GenesisConfig {

    public static final String SYSTEM_SENDER = "SYSTEM";

    public static final String GENESIS_PREVIOUS_HASH = "0";

    // fixed timestamp so every node creates the same genesis hash.
    public static final long GENESIS_TIMESTAMP = 0L;

    public static final int GENESIS_AMOUNT = Integer.parseInt(System.getenv().getOrDefault("GENESIS_AMOUNT", "1000"));

    public static final String FAUCET_PUBLIC_KEY_BASE64 = System.getenv("FAUCET_PUBLIC_KEY_BASE64");

    private GenesisConfig() {
        // to prevent instantiating, but use it only with static constants
    }
}
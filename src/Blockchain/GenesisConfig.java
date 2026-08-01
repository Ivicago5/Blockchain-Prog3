package Blockchain;

public class GenesisConfig {

    public static final String SYSTEM_SENDER = "SYSTEM";

    public static final String GENESIS_PREVIOUS_HASH = "0";

    public static final int MINING_REWARD = Integer.parseInt(System.getenv().getOrDefault("MINING_REWARD", "10"));

    public static final int BLOCK_DIFFICULTY = Integer.parseInt(System.getenv().getOrDefault("BLOCK_DIFFICULTY", "4"));
    // fixed timestamp so every node creates the same genesis hash.
    public static final long GENESIS_TIMESTAMP = 0L;

    public static final int GENESIS_AMOUNT = Integer.parseInt(System.getenv().getOrDefault("GENESIS_AMOUNT", "1000"));

    public static final String FAUCET_PUBLIC_KEY_BASE64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE56STsl5n4MU1fxMyPxhOcqD2fh28pYFv9Vb+3BMrfbxuaG8ytffiflmBv/KMu4OnXQo0eEEmOYH6w4aXmBoVmg=="; //System.getenv("FAUCET_PUBLIC_KEY_BASE64");

    private GenesisConfig() {
        // to prevent instantiating, but use it only with static constants
    }
}
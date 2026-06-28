package Transaction;

public class UTXO {
    private final String id;
    private final String owner;
    private final String parentTxId;
    private final int amount;
    private boolean spent;

    public UTXO(String id, String owner, int amount, String parentTxId){
        this.id = id;
        this.owner = owner;
        this.amount = amount;
        this.parentTxId = parentTxId;
    }

    public String getId(){
        return id;
    }

    public int getAmount() {
        return amount;
    }

    public String getOwner() {
        return owner;
    }

    public UTXO copy() {
        return new UTXO(
                this.id,
                this.owner,
                this.amount,
                this.parentTxId
        );
    }

    private String shortKey(String key) {
        if (key == null) return "null";

        int maxLen = 12;

        if (key.length() <= maxLen) {
            return key;
        }

        return key.substring(0, maxLen) + "...";
    }

    @Override
    public String toString() {
        return "UTXO[" +
                "owner=" + shortKey(owner) +
                ", amount=" + amount +
                "]";
    }

}

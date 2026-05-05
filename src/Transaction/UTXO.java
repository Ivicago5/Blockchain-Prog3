package Transaction;

public class UTXO {
    private final String id;
    private final String owner;
    private final String parentTxId;
    private final int amount;

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

    @Override
    public String toString() {
        return "UTXO[" +
                "owner=" + owner.substring(0, 12) + "..." +
                ", amount=" + amount +
                "]";
    }

}

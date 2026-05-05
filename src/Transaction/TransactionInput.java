package Transaction;

public class TransactionInput {

    private final String utxoId;

    public TransactionInput(String utxoId){
        this.utxoId = utxoId;
    }

    public String getUtxoId() {
        return utxoId;
    }
}

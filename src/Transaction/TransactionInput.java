package Transaction;

import Util.JsonUtil;

public class TransactionInput {

    private final String utxoId;

    public TransactionInput(String utxoId){
        this.utxoId = utxoId;
    }

    public String getUtxoId() {
        return utxoId;
    }

    public String toJson() {
        return "{"
                + "\"utxoId\":\"" + JsonUtil.escape(utxoId) + "\""
                + "}";
    }


    public static TransactionInput fromJson(String json) {

        String utxoId = JsonUtil.extractString(json, "utxoId");

        return new TransactionInput(utxoId);
    }

}

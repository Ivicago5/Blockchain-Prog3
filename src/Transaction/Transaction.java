package Transaction;

import Blockchain.GenesisConfig;
import Util.Crypto;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

public class Transaction {

    private final String txId;
    private final int amount;
    private final String senderPublicKey;
    private final String receiverPublicKey;

    private final List<TransactionInput> inputs;
    private final List<UTXO> outputs;


    private String signature;


    public Transaction(String senderPublicKey, String receiverPublicKey, int amount, List <TransactionInput> inputs) {
        this.amount = amount;
        this.senderPublicKey = senderPublicKey;
        this.receiverPublicKey = receiverPublicKey;
        if (inputs == null){
            this.inputs = new ArrayList<>();
        } else {
            this.inputs = new ArrayList<>(inputs);
        }
        this.outputs = new ArrayList<>();
        this.txId = Crypto.SHA256Hash(getRawData());  // id without the signature
    }

    private String getRawData() {
        StringBuilder inputData = new StringBuilder();

        for (TransactionInput input : inputs) {
            inputData.append(input.getUtxoId()).append("|");
        }
        return senderPublicKey + "|" + receiverPublicKey + "|" + amount + "|" + inputData;
    }

    public void sign(PrivateKey senderPrivateKey){
        if (signature != null){
            throw new IllegalStateException("Transaction is already signed");
        }
        this.signature = Crypto.signECDSA(senderPrivateKey, txId);
    }

    public boolean isSignatureValid() {
        if (isSystemTransaction()) {
            return true;
        }

        if (signature == null || signature.isEmpty()) return false;

        return Crypto.verifyECDSA(senderPublicKey, txId, signature);
    }

    public int getAmount() {
        return amount;
    }

    public String getSenderPubKey() {
        return senderPublicKey;
    }

    public String getReceiverPubKey() {
        return receiverPublicKey;
    }

    public String getTxId() {
        return txId;
    }

    public List<TransactionInput> getInputs() {
        return new ArrayList<>(inputs);
    }

    public boolean isSystemTransaction() {
        return GenesisConfig.SYSTEM_SENDER.equals(senderPublicKey);
    }

    // asked an LLM to make debugging prettier so its more readable because Base64 was too long, this is what I got :)
    public String toDebugString() {
        return "TX[" +
                "from=" + shortKey(senderPublicKey) +
                ", to=" + shortKey(receiverPublicKey) +
                ", amount=" + amount +
                ", signed=" + (signature != null) +
                "]";
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
        return "Sender: " + senderPublicKey + ", Receiver: " + receiverPublicKey + ", Amount sent: " + amount + ", Transaction id: " + txId + ", Signature: " + (signature == null ? "" : signature) + " \n ";
    }


}



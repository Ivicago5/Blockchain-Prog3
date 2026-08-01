package Transaction;

import Blockchain.GenesisConfig;
import Util.Crypto;
import Util.JsonUtil;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;

public class Transaction {

    private final String txId;
    private final int amount;
    private final String senderPublicKey;
    private final String receiverPublicKey;
    private final String type;
    private String uniqueID;

    private final List<TransactionInput> inputs;
    private final List<UTXO> outputs;


    private String signature;


    public Transaction(String type, String senderPublicKey, String receiverPublicKey, int amount, List <TransactionInput> inputs, String uniqueID) {
        this.type = type;
        this.amount = amount;
        this.senderPublicKey = senderPublicKey;
        this.receiverPublicKey = receiverPublicKey;
        if (inputs == null){
            this.inputs = new ArrayList<>();
        } else {
            this.inputs = new ArrayList<>(inputs);
        }
        this.outputs = new ArrayList<>();
        this.uniqueID = uniqueID == null ? "" : uniqueID;
        this.txId = Crypto.SHA256Hash(getRawData());

    }

    private String getRawData() {
        StringBuilder inputData = new StringBuilder();

        for (TransactionInput input : inputs) {
            inputData.append(input.getUtxoId()).append("|");
        }
        return type + "|" + senderPublicKey + "|" + receiverPublicKey + "|" + amount + "|" + uniqueID + "|" + inputData;
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

    public String getType() {
        return type;
    }

    public List<TransactionInput> getInputs() {
        return new ArrayList<>(inputs);
    }

    public String getSignature() {
        return signature;
    }

    public boolean isSystemTransaction() {
        return GenesisConfig.SYSTEM_SENDER.equals(senderPublicKey) &&
                (type.equals("GENESIS") || type.equals("FAUCET") || type.equals("MINING_REWARD"));
    }

    public boolean isTransfer() {
        return type.equals("TRANSFER");
    }

    public boolean isGenesis() {
        return type.equals("GENESIS");
    }

    public boolean isFaucetTransaction() {
        return type.equals("FAUCET");
    }

    public boolean isMiningReward() {
        return type.equals("MINING_REWARD");
    }

    // asked a LLM to make debugging prettier so its more readable because Base64 was too long, this is what I got :)
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

    //Network

    public String toJson() {

        StringBuilder json = new StringBuilder();

        json.append("{");

        json.append("\"type\":\"")
                .append(Util.JsonUtil.escape(type))
                .append("\",");

        json.append("\"uniqueID\":\"")
                .append(JsonUtil.escape(uniqueID))
                .append("\",");

        json.append("\"txId\":\"")
                .append(Util.JsonUtil.escape(txId))
                .append("\",");

        json.append("\"amount\":")
                .append(amount)
                .append(",");

        json.append("\"senderPublicKey\":\"")
                .append(Util.JsonUtil.escape(senderPublicKey))
                .append("\",");

        json.append("\"receiverPublicKey\":\"")
                .append(Util.JsonUtil.escape(receiverPublicKey))
                .append("\",");

        json.append("\"signature\":\"")
                .append(Util.JsonUtil.escape(signature))
                .append("\",");

        json.append("\"inputs\":[");

        for (int i = 0; i < inputs.size(); i++) {

            json.append(inputs.get(i).toJson());

            if (i < inputs.size() - 1) {
                json.append(",");
            }
        }

        json.append("]");

        json.append("}");

        return json.toString();
    }

    public static Transaction fromJson(String json) {

        String type = Util.JsonUtil.extractString(json, "type");

        String uniqueID = Util.JsonUtil.extractString(json, "uniqueID");

        String txId = Util.JsonUtil.extractString(json, "txId");

        int amount = Util.JsonUtil.extractInt(json, "amount");

        String sender = Util.JsonUtil.extractString(json, "senderPublicKey");

        String receiver = Util.JsonUtil.extractString(json, "receiverPublicKey");

        String signature = Util.JsonUtil.extractString(json, "signature");

        String inputsJson = Util.JsonUtil.extractArray(json, "inputs");


        List<TransactionInput> inputs = new ArrayList<>();

        for (String inputJson : Util.JsonUtil.splitJsonArray(inputsJson)) {

            inputs.add(
                    TransactionInput.fromJson(inputJson)
            );
        }


        return Transaction.fromNetwork(
                type,
                uniqueID,
                txId,
                sender,
                receiver,
                amount,
                inputs,
                signature
        );
    }

    public static Transaction fromNetwork(
            String type,
            String uniqueID,
            String expectedTxId,
            String senderPublicKey,
            String receiverPublicKey,
            int amount,
            List<TransactionInput> inputs,
            String signature
    ) {
        if (expectedTxId == null || expectedTxId.isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is missing");
        }

        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Transaction type is missing");
        }

        if (senderPublicKey == null || senderPublicKey.isEmpty()) {
            throw new IllegalArgumentException("Sender public key is missing");
        }

        if (receiverPublicKey == null || receiverPublicKey.isEmpty()) {
            throw new IllegalArgumentException("Receiver public key is missing");
        }

        if (amount <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }

        Transaction transaction = new Transaction(
                type,
                senderPublicKey,
                receiverPublicKey,
                amount,
                inputs,
                uniqueID
        );

        if ((type.equals("FAUCET") || type.equals("MINING_REWARD") || type.equals("GENESIS"))
                && !transaction.isSystemTransaction()) {
                    throw new IllegalArgumentException(
                            "Unauthorized system transaction"
                    );
        }

        if (!transaction.getTxId().equals(expectedTxId)) {
            throw new IllegalArgumentException(
                    "Received transaction ID does not match transaction contents"
            );
        }

        if (!transaction.isSystemTransaction()) {
            if (signature == null || signature.isEmpty()) {
                throw new IllegalArgumentException(
                        "Received transaction signature is missing"
                );
            }

            transaction.signature = signature;
        }

        return transaction;
    }


}



package Tests;

import Blockchain.Block;
import Blockchain.Blockchain;
import Transaction.TransactionInput;
import Util.Logger;

public class SerializationTest {

    public static void main(String[] args) {

//        TransactionInput input = new TransactionInput("test123");
//
//        String json = input.toJson();
//
//        Logger.info(json);
//
//        TransactionInput restored = TransactionInput.fromJson(json);
//
//        Logger.info(restored.getUtxoId());

        Blockchain blockchain = new Blockchain();

        Block block = blockchain.getLatestBlock();

        String json = block.toJson();

        System.out.println(json);


        Block copy = Block.fromJson(json);


        System.out.println(
                block.getHash().equals(copy.getHash())
        );

        System.out.println(
                block.calculateHash().equals(copy.getHash())
        );
    }
}

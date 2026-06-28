import Network.Node;
import Util.Logger;

public class NodeMain {

    public static void main(String[] args) {
        String nodeId = getenvOrDefault("NODE_ID", "node-local");
        int port = Integer.parseInt(getenvOrDefault("PORT", "8080"));

        String nodeAddress = getenvOrDefault(
                "NODE_ADDRESS",
                "http://localhost:" + port
        );

        String bootstrapNode = getenvOrDefault("BOOTSTRAP_NODE", "");

        boolean isBootstrap = Boolean.parseBoolean(
                getenvOrDefault("IS_BOOTSTRAP", "false")
        );

        Logger.info("Starting blockchain node...");
        Logger.info("NODE_ID = " + nodeId);
        Logger.info("PORT = " + port);
        Logger.info("NODE_ADDRESS = " + nodeAddress);
        Logger.info("BOOTSTRAP_NODE = " + bootstrapNode);
        Logger.info("IS_BOOTSTRAP = " + isBootstrap);

        Node node = new Node(
                nodeId,
                port,
                nodeAddress,
                bootstrapNode,
                isBootstrap
        );

        node.start();
    }

    private static String getenvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);

        if (value == null || value.isEmpty()) {
            return defaultValue;
        }

        return value;
    }
}